package fr.cocooning.core.chiffrement.room

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import fr.cocooning.core.chiffrement.StockageSignal
import fr.cocooning.core.securite.BaseChiffree

/*
 * Clés et sessions Signal de ce téléphone, dans une base SQLCipher dédiée dont la phrase est
 * enveloppée par l'Android Keystore (ENF-SEC-02, ENF-SEC-04). Cette base n'entre JAMAIS dans la
 * sauvegarde EF-CPT-08 : un nouveau téléphone crée une nouvelle identité (ADR 0003).
 */

@Entity(tableName = "identite_locale")
class IdentiteLocaleEntity(
    @PrimaryKey val id: Int = UNIQUE,
    val paire: ByteArray,
    @ColumnInfo(name = "id_enregistrement") val idEnregistrement: Int,
) {
    companion object {
        const val UNIQUE = 0
    }
}

@Entity(tableName = "identites", primaryKeys = ["nom", "appareil"])
class IdentiteEntity(val nom: String, val appareil: Int, val cle: ByteArray)

@Entity(tableName = "pre_cles")
class PreCleEntity(@PrimaryKey val id: Int, val enregistrement: ByteArray)

@Entity(tableName = "pre_cles_signees")
class PreCleSigneeEntity(@PrimaryKey val id: Int, val enregistrement: ByteArray)

@Entity(tableName = "pre_cles_kyber")
class PreCleKyberEntity(@PrimaryKey val id: Int, val enregistrement: ByteArray)

/** Ouvertures de session déjà vues avec la pré-clé Kyber de dernier recours (anti-rejeu). */
@Entity(tableName = "usages_kyber", primaryKeys = ["id_kyber", "id_signee", "cle_de_base"])
class UsageKyberEntity(
    @ColumnInfo(name = "id_kyber") val idKyber: Int,
    @ColumnInfo(name = "id_signee") val idSignee: Int,
    @ColumnInfo(name = "cle_de_base") val cleDeBase: ByteArray,
)

@Entity(tableName = "sessions", primaryKeys = ["nom", "appareil"])
class SessionEntity(val nom: String, val appareil: Int, val enregistrement: ByteArray)

@Entity(tableName = "cles_expediteur", primaryKeys = ["nom", "appareil", "distribution"])
class CleExpediteurEntity(val nom: String, val appareil: Int, val distribution: String, val enregistrement: ByteArray)

@Entity(tableName = "compteurs")
class CompteurEntity(@PrimaryKey val nom: String, val valeur: Int)

/** Accès bloquants : libsignal appelle son stockage de façon synchrone. */
@Dao
interface SignalDao {
    @Query("SELECT * FROM identite_locale WHERE id = ${IdentiteLocaleEntity.UNIQUE}")
    fun identiteLocale(): IdentiteLocaleEntity?

    @Upsert
    fun enregistrerIdentiteLocale(identite: IdentiteLocaleEntity)

    @Query("SELECT cle FROM identites WHERE nom = :nom AND appareil = :appareil")
    fun identite(nom: String, appareil: Int): ByteArray?

    @Upsert
    fun enregistrerIdentite(identite: IdentiteEntity)

    @Query("DELETE FROM identites WHERE nom = :nom AND appareil = :appareil")
    fun supprimerIdentite(nom: String, appareil: Int)

    @Query("SELECT enregistrement FROM pre_cles WHERE id = :id")
    fun preCle(id: Int): ByteArray?

    @Upsert
    fun enregistrerPreCle(preCle: PreCleEntity)

    @Query("DELETE FROM pre_cles WHERE id = :id")
    fun supprimerPreCle(id: Int)

    @Query("SELECT COUNT(*) FROM pre_cles")
    fun nombrePreCles(): Int

    @Query("SELECT enregistrement FROM pre_cles_signees WHERE id = :id")
    fun preCleSignee(id: Int): ByteArray?

    @Query("SELECT enregistrement FROM pre_cles_signees ORDER BY id")
    fun preClesSignees(): List<ByteArray>

    @Upsert
    fun enregistrerPreCleSignee(preCle: PreCleSigneeEntity)

    @Query("DELETE FROM pre_cles_signees WHERE id = :id")
    fun supprimerPreCleSignee(id: Int)

    @Query("SELECT enregistrement FROM pre_cles_kyber WHERE id = :id")
    fun preCleKyber(id: Int): ByteArray?

    @Query("SELECT enregistrement FROM pre_cles_kyber ORDER BY id")
    fun preClesKyber(): List<ByteArray>

    @Upsert
    fun enregistrerPreCleKyber(preCle: PreCleKyberEntity)

    @Query("DELETE FROM pre_cles_kyber WHERE id = :id")
    fun supprimerPreCleKyber(id: Int)

    /** -1 si le trio existait déjà. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun noterUsageKyber(usage: UsageKyberEntity): Long

    @Query("SELECT enregistrement FROM sessions WHERE nom = :nom AND appareil = :appareil")
    fun session(nom: String, appareil: Int): ByteArray?

    @Upsert
    fun enregistrerSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE nom = :nom AND appareil = :appareil")
    fun supprimerSession(nom: String, appareil: Int)

    @Query("DELETE FROM sessions WHERE nom = :nom")
    fun supprimerSessions(nom: String)

    @Query("SELECT appareil FROM sessions WHERE nom = :nom ORDER BY appareil")
    fun appareilsAvecSession(nom: String): List<Int>

    @Query("SELECT enregistrement FROM cles_expediteur WHERE nom = :nom AND appareil = :appareil AND distribution = :distribution")
    fun cleExpediteur(nom: String, appareil: Int, distribution: String): ByteArray?

    @Upsert
    fun enregistrerCleExpediteur(cle: CleExpediteurEntity)

    @Query("SELECT valeur FROM compteurs WHERE nom = :nom")
    fun compteur(nom: String): Int?

    @Upsert
    fun enregistrerCompteur(compteur: CompteurEntity)
}

@Database(
    entities = [
        IdentiteLocaleEntity::class, IdentiteEntity::class, PreCleEntity::class, PreCleSigneeEntity::class,
        PreCleKyberEntity::class, UsageKyberEntity::class, SessionEntity::class, CleExpediteurEntity::class,
        CompteurEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BaseSignal : RoomDatabase() {
    abstract fun signalDao(): SignalDao

    companion object {
        /** Ne jamais l'ajouter à la sauvegarde EF-CPT-08 (ADR 0003). */
        const val NOM = "cocooning_signal.db"

        /** Base chiffrée. La clé est préparée à la première ouverture, hors du fil principal. */
        fun creer(context: Context): BaseSignal =
            Room.databaseBuilder(context.applicationContext, BaseSignal::class.java, NOM)
                .openHelperFactory(BaseChiffree.fabrique(context, NOM))
                .build()
    }
}

/** [StockageSignal] adossé à Room. */
class StockageSignalRoom(private val base: BaseSignal) : StockageSignal {
    private val dao = base.signalDao()

    override fun identiteLocale() =
        dao.identiteLocale()?.let { StockageSignal.IdentiteLocale(it.paire, it.idEnregistrement) }

    override fun enregistrerIdentiteLocale(identite: StockageSignal.IdentiteLocale) =
        dao.enregistrerIdentiteLocale(IdentiteLocaleEntity(paire = identite.paire, idEnregistrement = identite.idEnregistrement))

    override fun identiteDistante(nom: String, appareil: Int) = dao.identite(nom, appareil)
    override fun enregistrerIdentiteDistante(nom: String, appareil: Int, cle: ByteArray) =
        dao.enregistrerIdentite(IdentiteEntity(nom, appareil, cle))
    override fun supprimerIdentiteDistante(nom: String, appareil: Int) = dao.supprimerIdentite(nom, appareil)

    override fun preCle(id: Int) = dao.preCle(id)
    override fun enregistrerPreCle(id: Int, enregistrement: ByteArray) = dao.enregistrerPreCle(PreCleEntity(id, enregistrement))
    override fun supprimerPreCle(id: Int) = dao.supprimerPreCle(id)
    override fun nombrePreCles() = dao.nombrePreCles()

    override fun preCleSignee(id: Int) = dao.preCleSignee(id)
    override fun preClesSignees() = dao.preClesSignees()
    override fun enregistrerPreCleSignee(id: Int, enregistrement: ByteArray) =
        dao.enregistrerPreCleSignee(PreCleSigneeEntity(id, enregistrement))
    override fun supprimerPreCleSignee(id: Int) = dao.supprimerPreCleSignee(id)

    override fun preCleKyber(id: Int) = dao.preCleKyber(id)
    override fun preClesKyber() = dao.preClesKyber()
    override fun enregistrerPreCleKyber(id: Int, enregistrement: ByteArray) =
        dao.enregistrerPreCleKyber(PreCleKyberEntity(id, enregistrement))
    override fun supprimerPreCleKyber(id: Int) = dao.supprimerPreCleKyber(id)

    override fun noterUsageKyber(idKyber: Int, idSignee: Int, cleDeBase: ByteArray): Boolean =
        dao.noterUsageKyber(UsageKyberEntity(idKyber, idSignee, cleDeBase)) == -1L

    override fun session(nom: String, appareil: Int) = dao.session(nom, appareil)
    override fun enregistrerSession(nom: String, appareil: Int, enregistrement: ByteArray) =
        dao.enregistrerSession(SessionEntity(nom, appareil, enregistrement))
    override fun supprimerSession(nom: String, appareil: Int) = dao.supprimerSession(nom, appareil)
    override fun supprimerSessions(nom: String) = dao.supprimerSessions(nom)
    override fun appareilsAvecSession(nom: String) = dao.appareilsAvecSession(nom)

    override fun cleExpediteur(nom: String, appareil: Int, distribution: String) = dao.cleExpediteur(nom, appareil, distribution)
    override fun enregistrerCleExpediteur(nom: String, appareil: Int, distribution: String, enregistrement: ByteArray) =
        dao.enregistrerCleExpediteur(CleExpediteurEntity(nom, appareil, distribution, enregistrement))

    override fun compteur(nom: String) = dao.compteur(nom)
    override fun enregistrerCompteur(nom: String, valeur: Int) = dao.enregistrerCompteur(CompteurEntity(nom, valeur))

    override fun <R> enTransaction(bloc: () -> R): R = base.runInTransaction<R> { bloc() }
}
