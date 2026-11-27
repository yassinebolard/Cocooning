package fr.cocooning.core.securite

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

/**
 * Bases locales chiffrées avec SQLCipher (ENF-SEC-04).
 *
 * Usage avec Room : `Room.databaseBuilder(…).openHelperFactory(BaseChiffree.fabrique(context, nom))`.
 *
 * À la première ouverture, et seulement à ce moment-là (hors du fil principal, quand Room ouvre
 * la base) :
 * - la phrase de passe est lue dans le [CoffreCles], ou créée ;
 * - une base en clair laissée par une version précédente est chiffrée sur place, sans perte ;
 * - une base chiffrée dont la clé est perdue (Keystore effacé, base restaurée sur un autre
 *   téléphone) est mise de côté sous un autre nom et une base vide la remplace : sans clé,
 *   elle ne peut plus être lue par personne, et l'application doit rester utilisable.
 */
object BaseChiffree {

    private const val JOURNAL = "BaseChiffree"

    fun fabrique(context: Context, nomBase: String): SupportSQLiteOpenHelper.Factory {
        val appli = context.applicationContext
        return SupportSQLiteOpenHelper.Factory { configuration ->
            AideDiffere(configuration.name) {
                val phrase = preparer(appli, nomBase)
                SupportOpenHelperFactory(phrase).create(configuration)
            }
        }
    }

    /** Charge SQLCipher, obtient la phrase de passe et met la base existante en état d'être ouverte. */
    private fun preparer(context: Context, nomBase: String): ByteArray {
        System.loadLibrary("sqlcipher")
        val coffre = coffre(context)
        val base = context.getDatabasePath(nomBase)
        val existante = coffre.lire(nomBase)

        return when (FichierBase.etat(base)) {
            EtatFichier.Absent -> existante ?: coffre.creer(nomBase)
            EtatFichier.EnClair -> {
                val phrase = existante ?: coffre.creer(nomBase)
                chiffrerSurPlace(base, phrase)
                phrase
            }
            EtatFichier.Chiffre -> existante ?: run {
                val ecartee = FichierBase.mettreDeCote(base, System.currentTimeMillis())
                Log.w(JOURNAL, "Clé de $nomBase perdue : base illisible mise de côté (${ecartee.name}).")
                coffre.creer(nomBase)
            }
        }
    }

    /**
     * Chiffre une base en clair avec `sqlcipher_export`, dans un fichier à côté, puis remplace
     * l'original. Si quelque chose échoue avant le remplacement, la base en clair reste intacte
     * et le chiffrement sera retenté à la prochaine ouverture.
     */
    internal fun chiffrerSurPlace(base: File, phrase: ByteArray) {
        require(PhraseDePasse.estHexadecimale(phrase)) { "Phrase de passe inattendue" }
        val provisoire = File(base.path + ".chiffrement")
        FichierBase.supprimer(provisoire)

        // CREATE_IF_NECESSARY est indispensable : ATTACH hérite des droits d'ouverture de la base
        // principale et ne pourrait pas créer le fichier chiffré sans lui.
        val enClair = SQLiteDatabase.openDatabase(
            base.path, "", null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY, null, null,
        )
        val objets: Int
        try {
            val version = enClair.version
            objets = nombreObjets(enClair)
            val chemin = provisoire.path.replace("'", "''")
            enClair.rawExecSQL("ATTACH DATABASE '$chemin' AS chiffree KEY '${String(phrase, Charsets.US_ASCII)}'")
            // rawExecSQL n'échoue pas toujours quand ATTACH échoue : on vérifie.
            check(basesAttachees(enClair).contains("chiffree")) { "La base chiffrée n'a pas pu être créée" }
            enClair.rawExecSQL("SELECT sqlcipher_export('chiffree')")
            enClair.rawExecSQL("PRAGMA chiffree.user_version = $version")
            enClair.rawExecSQL("DETACH DATABASE chiffree")
        } finally {
            enClair.close()
        }

        // Relecture de la copie avec la phrase de passe avant de toucher à l'original.
        val copie = SQLiteDatabase.openDatabase(provisoire.path, phrase, null, SQLiteDatabase.OPEN_READWRITE, null, null)
        try {
            check(nombreObjets(copie) == objets) { "Copie chiffrée incomplète" }
        } finally {
            copie.close()
        }

        FichierBase.supprimer(base)
        check(provisoire.renameTo(base)) { "Impossible de remplacer la base en clair" }
        Log.i(JOURNAL, "Base ${base.name} chiffrée ($objets tables et index).")
    }

    internal fun coffre(context: Context) = CoffreCles(File(context.noBackupFilesDir, "cles"))

    /**
     * Copie cohérente de la base ouverte [ouverte] dans [cible], chiffrée avec la même phrase de
     * passe, sans fermer Room : `sqlcipher_export` lit la base dans une seule transaction.
     * Rend la phrase de passe, que la sauvegarde emporte avec la copie.
     */
    internal fun instantane(context: Context, nomBase: String, ouverte: SupportSQLiteDatabase, cible: File): ByteArray {
        val phrase = coffre(context).lire(nomBase) ?: error("Phrase de passe de $nomBase introuvable")
        require(PhraseDePasse.estHexadecimale(phrase)) { "Phrase de passe inattendue" }
        FichierBase.supprimer(cible)
        val chemin = cible.path.replace("'", "''")
        val version = ouverte.version
        ouverte.execSQL("ATTACH DATABASE '$chemin' AS instantane KEY '${String(phrase, Charsets.US_ASCII)}'")
        try {
            ouverte.query("SELECT sqlcipher_export('instantane')").use { it.moveToFirst() }
            ouverte.execSQL("PRAGMA instantane.user_version = $version")
        } finally {
            ouverte.execSQL("DETACH DATABASE instantane")
        }
        return phrase
    }

    /**
     * Remplace la base [nomBase] par la copie [octets] venue d'une sauvegarde, et enregistre sa
     * phrase de passe sous la clé du Keystore de ce téléphone. La base doit être fermée (Room
     * fermé, ou pas encore ouvert). La copie est relue avant de remplacer quoi que ce soit.
     */
    internal fun restaurer(context: Context, nomBase: String, octets: ByteArray, phrase: ByteArray) {
        System.loadLibrary("sqlcipher")
        val base = context.getDatabasePath(nomBase).also { it.parentFile?.mkdirs() }
        val provisoire = File(base.path + ".restauration")
        FichierBase.supprimer(provisoire)
        provisoire.writeBytes(octets)
        val copie = SQLiteDatabase.openDatabase(provisoire.path, phrase, null, SQLiteDatabase.OPEN_READONLY, null, null)
        try {
            check(nombreObjets(copie) > 0) { "Copie de $nomBase vide" }
        } finally {
            copie.close()
        }
        FichierBase.supprimer(base)
        check(provisoire.renameTo(base)) { "Impossible de remettre en place $nomBase" }
        coffre(context).enregistrer(nomBase, phrase)
        Log.i(JOURNAL, "Base $nomBase restaurée depuis une sauvegarde.")
    }

    private fun nombreObjets(base: SQLiteDatabase): Int =
        base.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst(); it.getInt(0) }

    private fun basesAttachees(base: SQLiteDatabase): List<String> =
        base.rawQuery("PRAGMA database_list", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(1)) }
        }
}

/**
 * Aide d'ouverture créée à la demande : Room fabrique son aide dès `build()`, souvent sur le fil
 * principal, alors que lire le Keystore et chiffrer une ancienne base prennent du temps.
 * Le vrai travail attend donc la première lecture ou écriture, que Room fait en arrière-plan.
 */
private class AideDiffere(
    override val databaseName: String?,
    private val creer: () -> SupportSQLiteOpenHelper,
) : SupportSQLiteOpenHelper {

    private var walDemande: Boolean? = null

    @Volatile
    private var aide: SupportSQLiteOpenHelper? = null

    private fun reelle(): SupportSQLiteOpenHelper = aide ?: synchronized(this) {
        aide ?: creer().also { cree ->
            walDemande?.let(cree::setWriteAheadLoggingEnabled)
            aide = cree
        }
    }

    override val writableDatabase: SupportSQLiteDatabase
        get() = reelle().writableDatabase

    override val readableDatabase: SupportSQLiteDatabase
        get() = reelle().readableDatabase

    override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
        synchronized(this) {
            val cree = aide
            if (cree != null) cree.setWriteAheadLoggingEnabled(enabled) else walDemande = enabled
        }
    }

    override fun close() {
        synchronized(this) { aide?.close() }
    }
}

internal enum class EtatFichier { Absent, EnClair, Chiffre }

/** Opérations sur le fichier d'une base et ses fichiers compagnons (journal, WAL). */
internal object FichierBase {

    /** Les 16 premiers octets de toute base SQLite en clair. */
    private val ENTETE_SQLITE = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    private val COMPAGNONS = listOf("-wal", "-shm", "-journal")

    fun etat(base: File): EtatFichier {
        if (!base.exists() || base.length() == 0L) return EtatFichier.Absent
        val entete = ByteArray(ENTETE_SQLITE.size)
        val lus = base.inputStream().use { it.read(entete) }
        return if (estEnClair(entete.copyOf(maxOf(lus, 0)))) EtatFichier.EnClair else EtatFichier.Chiffre
    }

    fun estEnClair(entete: ByteArray): Boolean =
        entete.size >= ENTETE_SQLITE.size && entete.copyOf(ENTETE_SQLITE.size).contentEquals(ENTETE_SQLITE)

    /** Renomme la base et ses compagnons en `<nom>.illisible-<horodatage>` ; rend le nouveau fichier. */
    fun mettreDeCote(base: File, horodatage: Long): File {
        val cible = File(base.path + ".illisible-$horodatage")
        base.renameTo(cible)
        COMPAGNONS.forEach { suffixe -> File(base.path + suffixe).renameTo(File(cible.path + suffixe)) }
        return cible
    }

    fun supprimer(base: File) {
        base.delete()
        COMPAGNONS.forEach { File(base.path + it).delete() }
    }
}
