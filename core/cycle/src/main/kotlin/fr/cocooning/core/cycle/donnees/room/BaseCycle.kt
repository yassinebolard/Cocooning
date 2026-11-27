package fr.cocooning.core.cycle.donnees.room

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.cocooning.core.cycle.domaine.Contraception
import fr.cocooning.core.cycle.domaine.CycleSaisi
import fr.cocooning.core.cycle.domaine.ParametresCycle
import fr.cocooning.core.cycle.donnees.SourceCycles
import fr.cocooning.core.securite.BaseChiffree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/*
 * Stockage local du cycle avec Room et SQLCipher (ENF-SEC-04, cahier des charges section 7).
 * La base est chiffrée sur le téléphone de madame ; sa clé est protégée par l'Android Keystore
 * (voir [BaseChiffree]). Une base en clair laissée par une version précédente est chiffrée
 * à la première ouverture, sans perte.
 */

@Entity(tableName = "cycles", indices = [Index(value = ["debut"], unique = true)])
data class CycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debut: LocalDate,
    @ColumnInfo(name = "fin_regles") val finRegles: LocalDate?,
)

/** Une seule ligne, d'identifiant [UNIQUE]. */
@Entity(tableName = "parametres_cycle")
data class ParametresCycleEntity(
    @PrimaryKey val id: Int = UNIQUE,
    @ColumnInfo(name = "duree_cycle") val dureeCycle: Int,
    @ColumnInfo(name = "duree_regles") val dureeRegles: Int,
    /** Nom de la valeur de [Contraception], nul si madame n'a rien indiqué. Ajouté en version 2. */
    val contraception: String? = null,
) {
    companion object {
        const val UNIQUE = 0
    }
}

/** Les dates sont stockées en nombre de jours depuis le 1er janvier 1970 : pas de fuseau, tri naturel. */
class ConvertisseursDate {
    @TypeConverter
    fun versJours(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun depuisJours(jours: Long?): LocalDate? = jours?.let(LocalDate::ofEpochDay)
}

@Dao
interface CycleDao {
    @Query("SELECT * FROM cycles ORDER BY debut")
    fun observerCycles(): Flow<List<CycleEntity>>

    @Query("SELECT * FROM cycles ORDER BY debut")
    suspend fun cycles(): List<CycleEntity>

    @Insert
    suspend fun inserer(cycle: CycleEntity): Long

    @Update
    suspend fun modifier(cycle: CycleEntity)

    @Query("DELETE FROM cycles WHERE id = :id")
    suspend fun supprimer(id: Long)

    @Query("SELECT * FROM parametres_cycle WHERE id = ${ParametresCycleEntity.UNIQUE}")
    fun observerParametres(): Flow<ParametresCycleEntity?>

    @Query("SELECT * FROM parametres_cycle WHERE id = ${ParametresCycleEntity.UNIQUE}")
    suspend fun parametres(): ParametresCycleEntity?

    @Upsert
    suspend fun enregistrerParametres(parametres: ParametresCycleEntity)
}

@Database(
    entities = [CycleEntity::class, ParametresCycleEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(ConvertisseursDate::class)
abstract class BaseCycle : RoomDatabase() {
    abstract fun cycleDao(): CycleDao

    companion object {
        const val NOM = "cocooning_cycle.db"

        /** Base chiffrée. La clé est préparée à la première ouverture, hors du fil principal. */
        fun creer(context: Context): BaseCycle =
            Room.databaseBuilder(context.applicationContext, BaseCycle::class.java, NOM)
                .openHelperFactory(BaseChiffree.fabrique(context, NOM))
                .build()

        /** Accès à la base ouverte pour la sauvegarde (EF-CPT-08), sans exposer Room aux appelants. */
        fun pourSauvegarde(base: BaseCycle): () -> SupportSQLiteDatabase = { base.openHelper.writableDatabase }
    }
}

/** [SourceCycles] adossée à Room. */
class SourceCyclesRoom(private val base: BaseCycle) : SourceCycles {
    private val dao = base.cycleDao()

    override fun observerCycles(): Flow<List<CycleSaisi>> = dao.observerCycles().map { liste -> liste.map { it.versDomaine() } }
    override fun observerParametres(): Flow<ParametresCycle?> = dao.observerParametres().map { it?.versDomaine() }
    override suspend fun cycles(): List<CycleSaisi> = dao.cycles().map { it.versDomaine() }
    override suspend fun parametres(): ParametresCycle? = dao.parametres()?.versDomaine()
    override suspend fun ajouter(debut: LocalDate): Long = dao.inserer(CycleEntity(debut = debut, finRegles = null))
    override suspend fun modifier(cycle: CycleSaisi) = dao.modifier(CycleEntity(cycle.id, cycle.debut, cycle.finRegles))
    override suspend fun supprimer(cycleId: Long) = dao.supprimer(cycleId)

    override suspend fun enregistrerParametres(parametres: ParametresCycle) =
        dao.enregistrerParametres(
            ParametresCycleEntity(
                dureeCycle = parametres.dureeCycle,
                dureeRegles = parametres.dureeRegles,
                contraception = parametres.contraception?.name,
            ),
        )

    override suspend fun <R> enTransaction(bloc: suspend () -> R): R = base.withTransaction { bloc() }
}

private fun CycleEntity.versDomaine() = CycleSaisi(id, debut, finRegles)

private fun ParametresCycleEntity.versDomaine() = ParametresCycle(
    dureeCycle = dureeCycle,
    dureeRegles = dureeRegles,
    contraception = Contraception.entries.firstOrNull { it.name == contraception },
)
