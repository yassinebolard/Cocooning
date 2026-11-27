package fr.cocooning.core.securite.sauvegarde

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.cocooning.core.securite.BaseChiffree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Sauvegarde des clés et de l'historique local pour le changement de téléphone (EF-CPT-08,
 * EF-CPT-09). Le fichier produit par [exporter] contient, chiffrés par la phrase de récupération :
 * chaque base (copie encore chiffrée par SQLCipher), sa phrase de passe, et les préférences.
 *
 * Pour l'instant l'utilisateur range lui-même ce fichier (PC, clé USB, Drive) ; il sera envoyé
 * chaque jour au serveur Cocooning quand celui-ci existera, sans que le serveur puisse le lire.
 *
 * @param bases les bases à emporter, par nom de fichier, avec l'accès à la base ouverte par Room.
 */
class SauvegardeCocooning(
    context: Context,
    private val bases: Map<String, () -> SupportSQLiteDatabase>,
    private val preferences: Map<String, DataStore<Preferences>> = emptyMap(),
) {
    private val appli = context.applicationContext

    /** Produit le fichier de sauvegarde. Long (Argon2id) : appelé hors du fil principal. */
    suspend fun exporter(phrase: CharSequence): ByteArray = withContext(Dispatchers.IO) {
        val entrees = linkedMapOf<String, ByteArray>()
        entrees[INFO_FORMAT] = byteArrayOf(1)
        val temporaire = File(appli.noBackupFilesDir, "sauvegarde").also { it.mkdirs() }
        bases.forEach { (nom, ouvrir) ->
            val copie = File(temporaire, "$nom.instantane")
            try {
                entrees[CLE + nom] = BaseChiffree.instantane(appli, nom, ouvrir(), copie)
                entrees[BASE + nom] = copie.readBytes()
            } finally {
                listOf("", "-wal", "-shm", "-journal").forEach { File(copie.path + it).delete() }
            }
        }
        preferences.forEach { (nom, magasin) ->
            val octets = ByteArrayOutputStream().also { PreferencesFileSerializer.writeTo(magasin.data.first(), it) }
            entrees[PREFERENCES + nom] = octets.toByteArray()
        }
        try {
            SauvegardeChiffree.sceller(ContenuSauvegarde(entrees), phrase)
        } finally {
            entrees.values.forEach { it.fill(0) }
        }
    }

    /**
     * Vérifie la phrase et rend le contenu, sans rien modifier. Lève [EchecOuverture].
     * Séparé de [restaurer] pour dire tout de suite à l'utilisateur si la phrase est fausse.
     */
    suspend fun lire(fichier: ByteArray, phrase: CharSequence): ContenuSauvegarde =
        withContext(Dispatchers.Default) { SauvegardeChiffree.ouvrir(fichier, phrase) }

    /**
     * Remet en place les bases et les préférences du [contenu]. Les bases concernées doivent être
     * fermées, et l'application redémarrée ensuite pour que Room les rouvre.
     */
    suspend fun restaurer(contenu: ContenuSauvegarde) = withContext(Dispatchers.IO) {
        // Seulement les bases connues de cette version : un nom venu du fichier ne choisit pas
        // où l'on écrit sur le disque.
        bases.keys.forEach { nom ->
            val copie = contenu[BASE + nom] ?: return@forEach
            val phrase = contenu[CLE + nom] ?: error("Phrase de passe de $nom absente de la sauvegarde")
            BaseChiffree.restaurer(appli, nom, copie, phrase)
        }
        preferences.forEach { (nom, magasin) ->
            val octets = contenu[PREFERENCES + nom] ?: return@forEach
            val relues = PreferencesFileSerializer.readFrom(octets.inputStream())
            magasin.updateData { relues }
        }
    }

    companion object {
        private const val INFO_FORMAT = "info/format"
        internal const val CLE = "cle/"
        internal const val BASE = "base/"
        internal const val PREFERENCES = "preferences/"
    }
}
