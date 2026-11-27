package fr.cocooning.core.messagerie.donnees.room

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.cocooning.core.messagerie.domaine.Accuse
import fr.cocooning.core.messagerie.domaine.ContenuMessage
import fr.cocooning.core.messagerie.domaine.EtatEnvoi
import fr.cocooning.core.messagerie.domaine.IdConversation
import fr.cocooning.core.messagerie.domaine.IdMembre
import fr.cocooning.core.messagerie.domaine.IdMessage
import fr.cocooning.core.messagerie.domaine.Message
import fr.cocooning.core.messagerie.domaine.Reaction
import fr.cocooning.core.messagerie.donnees.SourceMessages
import fr.cocooning.core.securite.BaseChiffree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/*
 * Historique local des messages avec Room et SQLCipher (ENF-SEC-04). Cette base fait partie de
 * la sauvegarde EF-CPT-08 ; les clés de chiffrement de bout en bout n'y sont pas (ADR 0003).
 * Les messages y sont en clair, protégés par le chiffrement de la base.
 */

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversation", "cree_le"]), Index(value = ["sortant", "etat_envoi"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversation: String,
    val auteur: String,
    val sortant: Boolean,
    /** Type de contenu (« texte »), nul quand le contenu a été effacé. */
    val type: String?,
    val corps: String?,
    @ColumnInfo(name = "reponse_a") val reponseA: String?,
    @ColumnInfo(name = "cree_le") val creeLe: Instant,
    @ColumnInfo(name = "arrive_le") val arriveLe: Instant,
    @ColumnInfo(name = "etat_envoi") val etatEnvoi: String,
    @ColumnInfo(name = "supprime_pour_tous") val supprimePourTous: Boolean,
    val masque: Boolean,
) {
    companion object {
        const val TYPE_TEXTE = "texte"
    }
}

/** Un par destinataire d'un message sortant. */
@Entity(
    tableName = "accuses",
    primaryKeys = ["message", "destinataire"],
    foreignKeys = [
        ForeignKey(entity = MessageEntity::class, parentColumns = ["id"], childColumns = ["message"], onDelete = ForeignKey.CASCADE),
    ],
)
data class AccuseEntity(
    val message: String,
    val destinataire: String,
    @ColumnInfo(name = "recu_le") val recuLe: Instant?,
    @ColumnInfo(name = "lu_le") val luLe: Instant?,
)

@Entity(
    tableName = "reactions",
    primaryKeys = ["message", "membre"],
    foreignKeys = [
        ForeignKey(entity = MessageEntity::class, parentColumns = ["id"], childColumns = ["message"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ReactionEntity(
    val message: String,
    val membre: String,
    val emoji: String,
)

data class MessageComplet(
    @Embedded val message: MessageEntity,
    @Relation(parentColumn = "id", entityColumn = "message") val accuses: List<AccuseEntity>,
    @Relation(parentColumn = "id", entityColumn = "message") val reactions: List<ReactionEntity>,
)

/** Heures stockées en millisecondes depuis le 1er janvier 1970 (UTC). */
class ConvertisseursHeure {
    @TypeConverter
    fun versMillis(heure: Instant?): Long? = heure?.toEpochMilli()

    @TypeConverter
    fun depuisMillis(millis: Long?): Instant? = millis?.let(Instant::ofEpochMilli)
}

@Dao
interface MessageDao {
    @Transaction
    @Query("SELECT * FROM messages WHERE conversation = :conversation")
    fun observerConversation(conversation: String): Flow<List<MessageComplet>>

    @Transaction
    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun message(id: String): MessageComplet?

    @Transaction
    @Query("SELECT * FROM messages WHERE sortant = 1 AND etat_envoi = :etat ORDER BY cree_le")
    suspend fun parEtat(etat: String): List<MessageComplet>

    @Insert
    suspend fun inserer(message: MessageEntity)

    @Insert
    suspend fun insererAccuses(accuses: List<AccuseEntity>)

    @Upsert
    suspend fun enregistrerAccuse(accuse: AccuseEntity)

    @Insert
    suspend fun insererReactions(reactions: List<ReactionEntity>)

    @Query("DELETE FROM reactions WHERE message = :id")
    suspend fun supprimerReactions(id: String)

    @Query("UPDATE messages SET etat_envoi = :etat WHERE id = :id")
    suspend fun modifierEtatEnvoi(id: String, etat: String): Int

    @Query("UPDATE messages SET masque = 1, type = NULL, corps = NULL, reponse_a = NULL WHERE id = :id")
    suspend fun masquer(id: String): Int

    @Query("UPDATE messages SET supprime_pour_tous = 1, type = NULL, corps = NULL, reponse_a = NULL WHERE id = :id")
    suspend fun effacerPourTous(id: String): Int
}

@Database(
    entities = [MessageEntity::class, AccuseEntity::class, ReactionEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(ConvertisseursHeure::class)
abstract class BaseMessagerie : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        const val NOM = "cocooning_messagerie.db"

        /** Base chiffrée. La clé est préparée à la première ouverture, hors du fil principal. */
        fun creer(context: Context): BaseMessagerie =
            Room.databaseBuilder(context.applicationContext, BaseMessagerie::class.java, NOM)
                .openHelperFactory(BaseChiffree.fabrique(context, NOM))
                .build()

        /** Accès à la base ouverte pour la sauvegarde (EF-CPT-08), sans exposer Room aux appelants. */
        fun pourSauvegarde(base: BaseMessagerie): () -> SupportSQLiteDatabase = { base.openHelper.writableDatabase }
    }
}

/** [SourceMessages] adossée à Room. */
class SourceMessagesRoom(private val base: BaseMessagerie) : SourceMessages {
    private val dao = base.messageDao()

    override fun observerConversation(conversation: IdConversation): Flow<List<Message>> =
        dao.observerConversation(conversation.valeur).map { liste -> liste.map { it.versDomaine() } }

    override suspend fun message(id: IdMessage): Message? = dao.message(id.valeur)?.versDomaine()

    override suspend fun enAttente(): List<Message> = dao.parEtat(EtatEnvoi.EnAttente.name).map { it.versDomaine() }

    override suspend fun inserer(message: Message) = base.withTransaction {
        dao.inserer(message.versEntite())
        dao.insererAccuses(message.accuses.map { it.versEntite(message.id) })
        dao.insererReactions(message.reactions.map { it.versEntite(message.id) })
    }

    override suspend fun modifierEtatEnvoi(id: IdMessage, etat: EtatEnvoi) =
        verifier(dao.modifierEtatEnvoi(id.valeur, etat.name), id)

    override suspend fun enregistrerAccuse(id: IdMessage, accuse: Accuse) = dao.enregistrerAccuse(accuse.versEntite(id))

    override suspend fun remplacerReactions(id: IdMessage, reactions: List<Reaction>) = base.withTransaction {
        dao.supprimerReactions(id.valeur)
        dao.insererReactions(reactions.map { it.versEntite(id) })
    }

    override suspend fun masquer(id: IdMessage) = base.withTransaction {
        verifier(dao.masquer(id.valeur), id)
        dao.supprimerReactions(id.valeur)
    }

    override suspend fun effacerPourTous(id: IdMessage) = base.withTransaction {
        verifier(dao.effacerPourTous(id.valeur), id)
        dao.supprimerReactions(id.valeur)
    }

    override suspend fun <R> enTransaction(bloc: suspend () -> R): R = base.withTransaction { bloc() }

    private fun verifier(lignes: Int, id: IdMessage) = check(lignes == 1) { "Message inconnu : ${id.valeur}" }
}

private fun MessageComplet.versDomaine() = Message(
    id = IdMessage(message.id),
    conversation = IdConversation(message.conversation),
    auteur = IdMembre(message.auteur),
    sortant = message.sortant,
    contenu = when (message.type) {
        MessageEntity.TYPE_TEXTE -> ContenuMessage.Texte(message.corps.orEmpty())
        else -> null
    },
    reponseA = message.reponseA?.let(::IdMessage),
    creeLe = message.creeLe,
    arriveLe = message.arriveLe,
    etatEnvoi = EtatEnvoi.valueOf(message.etatEnvoi),
    accuses = accuses.sortedBy { it.destinataire }.map { Accuse(IdMembre(it.destinataire), it.recuLe, it.luLe) },
    reactions = reactions.sortedBy { it.membre }.map { Reaction(IdMembre(it.membre), it.emoji) },
    supprimePourTous = message.supprimePourTous,
    masque = message.masque,
)

private fun Message.versEntite() = MessageEntity(
    id = id.valeur,
    conversation = conversation.valeur,
    auteur = auteur.valeur,
    sortant = sortant,
    type = when (contenu) {
        is ContenuMessage.Texte -> MessageEntity.TYPE_TEXTE
        null -> null
    },
    corps = (contenu as? ContenuMessage.Texte)?.corps,
    reponseA = reponseA?.valeur,
    creeLe = creeLe,
    arriveLe = arriveLe,
    etatEnvoi = etatEnvoi.name,
    supprimePourTous = supprimePourTous,
    masque = masque,
)

private fun Accuse.versEntite(message: IdMessage) = AccuseEntity(message.valeur, destinataire.valeur, recuLe, luLe)

private fun Reaction.versEntite(message: IdMessage) = ReactionEntity(message.valeur, membre.valeur, emoji)
