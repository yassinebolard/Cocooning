package fr.cocooning.core.reseau

import fr.cocooning.core.messagerie.domaine.ErreurMessagerie
import fr.cocooning.core.messagerie.domaine.IdMembre
import fr.cocooning.core.messagerie.domaine.IdMessage
import fr.cocooning.core.messagerie.domaine.Message
import fr.cocooning.core.messagerie.donnees.MessagerieRepository
import fr.cocooning.core.messagerie.donnees.ResultatMessagerie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll

/** Où en est ce téléphone avec le serveur. */
enum class EtatInscription {
    /** Aucun espace : créer ou rejoindre. */
    NonInscrit,

    /** Une sauvegarde a été restaurée : reprendre la place de l'ancien téléphone (EF-CPT-09). */
    AReprendre,
    Inscrit,
}

/** Ce dont l'onglet Messages a besoin, sans rien savoir du réseau ni du chiffrement. */
interface ServiceMessagerie {
    suspend fun etat(): EtatInscription

    /** Crée l'espace ; renvoie le code d'invitation et son expiration (millisecondes). */
    suspend fun creerEspace(): Pair<String, Long>
    suspend fun rejoindre(code: String)
    suspend fun reprendre()

    suspend fun moi(): IdMembre

    /** Messages visibles de la conversation du couple, à chaque changement. */
    fun conversation(): Flow<List<Message>>

    /** Écrit un texte pour les autres membres ; il part à la prochaine synchronisation. */
    suspend fun ecrire(texte: String, reponseA: IdMessage? = null): ResultatMessagerie
    suspend fun reessayer(id: IdMessage): ResultatMessagerie
    suspend fun synchroniser(): RapportSynchro
    suspend fun accuserLecture(messages: List<Message>)
}

/**
 * [ServiceMessagerie] de l'appli : [Synchroniseur] pour le serveur, et un dépôt local créé dès
 * que le membre est connu ([depot] n'est appelé qu'après l'inscription).
 */
class ServiceMessagerieReseau(
    private val synchro: Synchroniseur,
    private val compte: MagasinCompte,
    private val depot: suspend () -> MessagerieRepository,
) : ServiceMessagerie {

    override suspend fun etat(): EtatInscription = when {
        compte.lire() == null -> EtatInscription.NonInscrit
        synchro.doitReprendre() -> EtatInscription.AReprendre
        else -> EtatInscription.Inscrit
    }

    override suspend fun creerEspace(): Pair<String, Long> = synchro.creerEspace().let { it.code to it.expireLe }
    override suspend fun rejoindre(code: String) = synchro.rejoindre(code)
    override suspend fun reprendre() = synchro.reprendreApresRestauration()
    override suspend fun moi(): IdMembre = checkNotNull(synchro.moi()) { "Téléphone pas encore inscrit" }

    override fun conversation(): Flow<List<Message>> = flow {
        val conversation = synchro.conversation() ?: return@flow emitAll(emptyFlow())
        emitAll(depot().conversation(conversation))
    }

    override suspend fun ecrire(texte: String, reponseA: IdMessage?): ResultatMessagerie {
        val conversation = synchro.conversation() ?: return ResultatMessagerie.Refuse(ErreurMessagerie.AucunDestinataire)
        val partenaires = try {
            synchro.partenaires()
        } catch (e: ErreurReseau) {
            emptyList()
        }
        return depot().ecrire(conversation, partenaires, texte, reponseA)
    }

    override suspend fun reessayer(id: IdMessage): ResultatMessagerie = depot().reessayer(id)
    override suspend fun synchroniser(): RapportSynchro = synchro.synchroniser()
    override suspend fun accuserLecture(messages: List<Message>) = synchro.accuserLecture(messages)
}
