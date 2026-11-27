package fr.cocooning.core.messagerie.donnees

import fr.cocooning.core.messagerie.domaine.Accuse
import fr.cocooning.core.messagerie.domaine.ContenuMessage
import fr.cocooning.core.messagerie.domaine.ErreurMessagerie
import fr.cocooning.core.messagerie.domaine.EtatEnvoi
import fr.cocooning.core.messagerie.domaine.IdConversation
import fr.cocooning.core.messagerie.domaine.IdMembre
import fr.cocooning.core.messagerie.domaine.IdMessage
import fr.cocooning.core.messagerie.domaine.Message
import fr.cocooning.core.messagerie.domaine.MessageEntrant
import fr.cocooning.core.messagerie.domaine.ReglesMessages
import fr.cocooning.core.messagerie.domaine.TypeAccuse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Dépôt des messages sur le téléphone : applique [ReglesMessages] puis écrit dans [source].
 *
 * @param horloge heure du téléphone ; les heures sont gardées à la milliseconde, comme en base.
 * @param nouvelId identifiant d'un message écrit ici (UUID aléatoire par défaut).
 */
class MessagerieRepositoryLocal(
    private val source: SourceMessages,
    override val moi: IdMembre,
    private val horloge: Clock = Clock.systemUTC(),
    private val nouvelId: () -> IdMessage = { IdMessage(UUID.randomUUID().toString()) },
    private val longueurMax: Int = ReglesMessages.LONGUEUR_MAX_PAR_DEFAUT,
) : MessagerieRepository {

    override fun conversation(conversation: IdConversation): Flow<List<Message>> =
        source.observerConversation(conversation).map(ReglesMessages::visibles)

    override suspend fun ecrire(
        conversation: IdConversation,
        destinataires: Collection<IdMembre>,
        texte: String,
        reponseA: IdMessage?,
    ): ResultatMessagerie = protege {
        val corps = ReglesMessages.normaliser(texte)
        ReglesMessages.validerTexte(corps, longueurMax)?.let { return@protege refus(it) }
        val pour = destinataires.filterNot { it == moi }.distinct()
        if (pour.isEmpty()) return@protege refus(ErreurMessagerie.AucunDestinataire)
        if (reponseA != null && !ReglesMessages.peutCiter(source.message(reponseA), conversation)) {
            return@protege refus(ErreurMessagerie.CitationImpossible)
        }
        val maintenant = maintenant()
        val message = Message(
            id = nouvelId(),
            conversation = conversation,
            auteur = moi,
            sortant = true,
            contenu = ContenuMessage.Texte(corps),
            reponseA = reponseA,
            creeLe = maintenant,
            arriveLe = maintenant,
            etatEnvoi = EtatEnvoi.EnAttente,
            accuses = pour.map { Accuse(it) },
        )
        source.inserer(message)
        ResultatMessagerie.Fait(message)
    }

    override suspend fun aEnvoyer(): List<Message> = source.enAttente()

    override suspend fun marquerEnvoye(id: IdMessage): ResultatMessagerie = sortant(id) { message ->
        if (message.etatEnvoi != EtatEnvoi.Envoye) source.modifierEtatEnvoi(id, EtatEnvoi.Envoye)
        relire(id)
    }

    override suspend fun marquerEchec(id: IdMessage): ResultatMessagerie = sortant(id) { message ->
        when (message.etatEnvoi) {
            // Un échec signalé après coup ne fait pas oublier qu'il est parti.
            EtatEnvoi.Envoye -> refus(ErreurMessagerie.EtatInattendu)
            EtatEnvoi.Echec -> ResultatMessagerie.Fait(message)
            EtatEnvoi.EnAttente -> {
                source.modifierEtatEnvoi(id, EtatEnvoi.Echec)
                relire(id)
            }
        }
    }

    override suspend fun reessayer(id: IdMessage): ResultatMessagerie = sortant(id) { message ->
        when (message.etatEnvoi) {
            EtatEnvoi.Envoye -> refus(ErreurMessagerie.EtatInattendu)
            EtatEnvoi.EnAttente -> ResultatMessagerie.Fait(message)
            EtatEnvoi.Echec -> {
                source.modifierEtatEnvoi(id, EtatEnvoi.EnAttente)
                relire(id)
            }
        }
    }

    override suspend fun recevoir(entrant: MessageEntrant): ResultatMessagerie = protege {
        val existant = source.message(entrant.id)
        if (existant != null) {
            // Renvoi après une coupure : déjà enregistré, rien ne change.
            return@protege if (existant.auteur == entrant.auteur && existant.conversation == entrant.conversation) {
                ResultatMessagerie.Fait(existant)
            } else {
                refus(ErreurMessagerie.Incoherent)
            }
        }
        val corps = ReglesMessages.normaliser(entrant.texte)
        ReglesMessages.validerTexte(corps, longueurMax)?.let { return@protege refus(it) }
        val message = Message(
            id = entrant.id,
            conversation = entrant.conversation,
            auteur = entrant.auteur,
            sortant = false,
            contenu = ContenuMessage.Texte(corps),
            reponseA = entrant.reponseA,
            creeLe = entrant.creeLe.truncatedTo(ChronoUnit.MILLIS),
            arriveLe = maintenant(),
            etatEnvoi = EtatEnvoi.Envoye,
        )
        source.inserer(message)
        ResultatMessagerie.Fait(message)
    }

    override suspend fun appliquerAccuse(
        id: IdMessage,
        destinataire: IdMembre,
        type: TypeAccuse,
        le: Instant,
    ): ResultatMessagerie = protege {
        val message = source.message(id) ?: return@protege refus(ErreurMessagerie.MessageInconnu)
        if (!message.sortant) return@protege refus(ErreurMessagerie.AccuseInattendu)
        val actuel = message.accuses.firstOrNull { it.destinataire == destinataire }
            ?: return@protege refus(ErreurMessagerie.AccuseInattendu)
        val nouveau = ReglesMessages.appliquerAccuse(actuel, type, le.truncatedTo(ChronoUnit.MILLIS))
        if (nouveau != actuel) source.enregistrerAccuse(id, nouveau)
        // Un accusé prouve que le message est parti, même si la réponse du serveur s'est perdue.
        if (message.etatEnvoi != EtatEnvoi.Envoye) source.modifierEtatEnvoi(id, EtatEnvoi.Envoye)
        relire(id)
    }

    override suspend fun reagir(id: IdMessage, emoji: String?): ResultatMessagerie = protege {
        val message = source.message(id) ?: return@protege refus(ErreurMessagerie.MessageInconnu)
        when {
            message.masque -> refus(ErreurMessagerie.MessageInconnu)
            message.supprimePourTous -> refus(ErreurMessagerie.DejaSupprime)
            else -> {
                emoji?.let { ReglesMessages.validerReaction(it) }?.let { return@protege refus(it) }
                source.remplacerReactions(id, ReglesMessages.reagir(message.reactions, moi, emoji))
                relire(id)
            }
        }
    }

    override suspend fun recevoirReaction(id: IdMessage, membre: IdMembre, emoji: String?): ResultatMessagerie =
        protege {
            val message = source.message(id) ?: return@protege refus(ErreurMessagerie.MessageInconnu)
            // Message supprimé ici : la réaction n'a plus rien à décorer, on l'ignore sans erreur.
            if (message.masque || message.supprimePourTous) return@protege ResultatMessagerie.Fait(message)
            emoji?.let { ReglesMessages.validerReaction(it) }?.let { return@protege refus(it) }
            source.remplacerReactions(id, ReglesMessages.reagir(message.reactions, membre, emoji))
            relire(id)
        }

    override suspend fun supprimerPourMoi(id: IdMessage): ResultatMessagerie = protege {
        val message = source.message(id) ?: return@protege refus(ErreurMessagerie.MessageInconnu)
        if (!message.masque) source.masquer(id)
        relire(id)
    }

    override suspend fun supprimerPourTous(id: IdMessage): ResultatMessagerie = protege {
        val message = source.message(id) ?: return@protege refus(ErreurMessagerie.MessageInconnu)
        ReglesMessages.refusSuppressionPourTous(message, moi)?.let { return@protege refus(it) }
        source.effacerPourTous(id)
        relire(id)
    }

    override suspend fun recevoirSuppression(id: IdMessage, auteur: IdMembre): ResultatMessagerie = protege {
        val message = source.message(id) ?: return@protege refus(ErreurMessagerie.MessageInconnu)
        when {
            message.auteur != auteur -> refus(ErreurMessagerie.PasAuteur)
            message.supprimePourTous -> ResultatMessagerie.Fait(message)
            else -> {
                source.effacerPourTous(id)
                relire(id)
            }
        }
    }

    /** Opération sur un de mes messages ; un message reçu est refusé. */
    private suspend fun sortant(id: IdMessage, bloc: suspend (Message) -> ResultatMessagerie): ResultatMessagerie =
        protege {
            val message = source.message(id) ?: return@protege refus(ErreurMessagerie.MessageInconnu)
            if (!message.sortant) refus(ErreurMessagerie.EtatInattendu) else bloc(message)
        }

    private fun maintenant(): Instant = horloge.instant().truncatedTo(ChronoUnit.MILLIS)

    private suspend fun relire(id: IdMessage) = ResultatMessagerie.Fait(checkNotNull(source.message(id)))

    private fun refus(erreur: ErreurMessagerie) = ResultatMessagerie.Refuse(erreur)

    /** Frontière d'erreur : une exception du stockage devient un [ResultatMessagerie.Echec]. */
    private suspend fun protege(bloc: suspend () -> ResultatMessagerie): ResultatMessagerie = try {
        source.enTransaction { bloc() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ResultatMessagerie.Echec(e)
    }
}
