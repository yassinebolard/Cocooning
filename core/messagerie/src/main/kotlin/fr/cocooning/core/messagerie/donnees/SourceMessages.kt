package fr.cocooning.core.messagerie.donnees

import fr.cocooning.core.messagerie.domaine.Accuse
import fr.cocooning.core.messagerie.domaine.EtatEnvoi
import fr.cocooning.core.messagerie.domaine.IdConversation
import fr.cocooning.core.messagerie.domaine.IdMembre
import fr.cocooning.core.messagerie.domaine.IdMessage
import fr.cocooning.core.messagerie.domaine.Message
import fr.cocooning.core.messagerie.domaine.Reaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Lecture et écriture brutes des messages. Les règles sont dans [MessagerieRepositoryLocal]. */
interface SourceMessages {
    /** Tous les messages de la conversation, masqués compris, dans un ordre quelconque. */
    fun observerConversation(conversation: IdConversation): Flow<List<Message>>

    /** Un message, masqué ou non ; nul s'il n'existe pas. */
    suspend fun message(id: IdMessage): Message?

    /** Messages sortants en attente d'envoi, du plus ancien au plus récent. */
    suspend fun enAttente(): List<Message>

    /** Enregistre un nouveau message avec ses accusés et réactions. */
    suspend fun inserer(message: Message)

    suspend fun modifierEtatEnvoi(id: IdMessage, etat: EtatEnvoi)

    /** Remplace l'accusé du même destinataire. */
    suspend fun enregistrerAccuse(id: IdMessage, accuse: Accuse)

    /** Remplace toutes les réactions du message. */
    suspend fun remplacerReactions(id: IdMessage, reactions: List<Reaction>)

    /** Supprimé pour moi : contenu et réactions effacés, trace gardée. */
    suspend fun masquer(id: IdMessage)

    /** Supprimé pour les deux : contenu, citation et réactions effacés, mention gardée. */
    suspend fun effacerPourTous(id: IdMessage)

    /** Exécute [bloc] d'un seul tenant. */
    suspend fun <R> enTransaction(bloc: suspend () -> R): R
}

/** Source en mémoire, pour les tests et les aperçus. Rien n'est conservé. */
class SourceMessagesMemoire(initial: List<Message> = emptyList()) : SourceMessages {
    private val etat = MutableStateFlow(initial.associateBy { it.id })
    private val verrou = Mutex()

    /** Si non nul, chaque écriture échoue avec cette exception (tests de la frontière d'erreur). */
    var panne: Exception? = null

    override fun observerConversation(conversation: IdConversation): Flow<List<Message>> =
        etat.map { tous -> tous.values.filter { it.conversation == conversation } }

    override suspend fun message(id: IdMessage): Message? = etat.value[id]

    override suspend fun enAttente(): List<Message> =
        etat.value.values.filter { it.sortant && it.etatEnvoi == EtatEnvoi.EnAttente }.sortedBy { it.creeLe }

    override suspend fun inserer(message: Message) {
        panne?.let { throw it }
        check(message.id !in etat.value) { "Message déjà présent : ${message.id}" }
        etat.update { it + (message.id to message) }
    }

    override suspend fun modifierEtatEnvoi(id: IdMessage, etat: EtatEnvoi) = changer(id) { it.copy(etatEnvoi = etat) }

    override suspend fun enregistrerAccuse(id: IdMessage, accuse: Accuse) = changer(id) { message ->
        message.copy(accuses = message.accuses.filterNot { it.destinataire == accuse.destinataire } + accuse)
    }

    override suspend fun remplacerReactions(id: IdMessage, reactions: List<Reaction>) =
        changer(id) { it.copy(reactions = reactions) }

    override suspend fun masquer(id: IdMessage) =
        changer(id) { it.copy(masque = true, contenu = null, reponseA = null, reactions = emptyList()) }

    override suspend fun effacerPourTous(id: IdMessage) =
        changer(id) { it.copy(supprimePourTous = true, contenu = null, reponseA = null, reactions = emptyList()) }

    override suspend fun <R> enTransaction(bloc: suspend () -> R): R = verrou.withLock { bloc() }

    private fun changer(id: IdMessage, transformation: (Message) -> Message) {
        panne?.let { throw it }
        etat.update { tous ->
            val message = checkNotNull(tous[id]) { "Message inconnu : $id" }
            tous + (id to transformation(message))
        }
    }
}
