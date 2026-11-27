package fr.cocooning.core.messagerie.donnees

import fr.cocooning.core.messagerie.domaine.ErreurMessagerie
import fr.cocooning.core.messagerie.domaine.IdConversation
import fr.cocooning.core.messagerie.domaine.IdMembre
import fr.cocooning.core.messagerie.domaine.IdMessage
import fr.cocooning.core.messagerie.domaine.Message
import fr.cocooning.core.messagerie.domaine.MessageEntrant
import fr.cocooning.core.messagerie.domaine.TypeAccuse
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Résultat d'une opération. Les exceptions du stockage ne sortent jamais du dépôt. */
sealed interface ResultatMessagerie {
    /** @param message le message dans son nouvel état. */
    data class Fait(val message: Message) : ResultatMessagerie

    data class Refuse(val erreur: ErreurMessagerie) : ResultatMessagerie

    data class Echec(val cause: Throwable) : ResultatMessagerie
}

/**
 * Historique des messages sur ce téléphone, du point de vue de [moi].
 *
 * Ce dépôt ne parle pas au réseau : la couche de transport (à venir) lit [aEnvoyer], chiffre et
 * envoie, puis rapporte le résultat par [marquerEnvoye] ou [marquerEchec] ; elle livre ce qui
 * arrive par [recevoir], [appliquerAccuse], [recevoirReaction] et [recevoirSuppression].
 * Toutes les opérations venues du réseau sont idempotentes : rejouer un événement ne change rien.
 */
interface MessagerieRepository {
    val moi: IdMembre

    /** Messages visibles de la conversation, dans l'ordre d'affichage, à chaque changement. */
    fun conversation(conversation: IdConversation): Flow<List<Message>>

    /**
     * Écrit un texte (EF-MSG-01), éventuellement en citant [reponseA] (EF-MSG-08). Le message est
     * enregistré tout de suite en attente d'envoi, même sans réseau (EF-MSG-10).
     */
    suspend fun ecrire(
        conversation: IdConversation,
        destinataires: Collection<IdMembre>,
        texte: String,
        reponseA: IdMessage? = null,
    ): ResultatMessagerie

    /** Messages à envoyer, du plus ancien au plus récent. */
    suspend fun aEnvoyer(): List<Message>

    suspend fun marquerEnvoye(id: IdMessage): ResultatMessagerie
    suspend fun marquerEchec(id: IdMessage): ResultatMessagerie

    /** Remet en file d'attente un message en échec. */
    suspend fun reessayer(id: IdMessage): ResultatMessagerie

    /** Message arrivé d'un autre téléphone, déjà déchiffré. */
    suspend fun recevoir(entrant: MessageEntrant): ResultatMessagerie

    /** Accusé reçu ou lu d'un destinataire pour un de mes messages (EF-MSG-07). */
    suspend fun appliquerAccuse(id: IdMessage, destinataire: IdMembre, type: TypeAccuse, le: Instant): ResultatMessagerie

    /** Ma réaction : pose, remplace, ou retire si [emoji] est nul. */
    suspend fun reagir(id: IdMessage, emoji: String?): ResultatMessagerie

    /** Réaction d'un autre membre. */
    suspend fun recevoirReaction(id: IdMessage, membre: IdMembre, emoji: String?): ResultatMessagerie

    /** Supprime pour moi seulement (EF-MSG-09). */
    suspend fun supprimerPourMoi(id: IdMessage): ResultatMessagerie

    /** Supprime un de mes messages pour les deux (EF-MSG-09). */
    suspend fun supprimerPourTous(id: IdMessage): ResultatMessagerie

    /** Demande de suppression pour tous envoyée par l'auteur d'un message. */
    suspend fun recevoirSuppression(id: IdMessage, auteur: IdMembre): ResultatMessagerie
}
