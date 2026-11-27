package fr.cocooning.core.messagerie.domaine

import java.time.Instant

/*
 * Modèle des messages tels que le téléphone les conserve (EF-MSG-01, 07, 08, 09).
 * Rien ici ne dépend d'Android, du réseau ni du chiffrement : ce sont les messages en clair,
 * stockés dans la base locale chiffrée. Le format envoyé sur le réseau est décrit dans l'ADR 0003.
 */

/** Identifiant d'un message, créé par le téléphone de l'auteur avant tout envoi (clé d'idempotence). */
@JvmInline
value class IdMessage(val valeur: String)

/** Conversation d'un espace. En V1, un espace a une seule conversation, celle du couple. */
@JvmInline
value class IdConversation(val valeur: String)

/** Membre d'un espace (ENF-EVO-01 : N membres, même si la V1 n'en montre que deux). */
@JvmInline
value class IdMembre(val valeur: String)

/** Ce que contient un message. Seul le texte existe dans ce premier jalon. */
sealed interface ContenuMessage {
    data class Texte(val corps: String) : ContenuMessage
}

/** Où en est l'envoi d'un message écrit sur ce téléphone. */
enum class EtatEnvoi {
    /** Écrit, en file d'attente : il partira dès que le réseau le permet (EF-MSG-10). */
    EnAttente,

    /** Accepté par le serveur. */
    Envoye,

    /** Le serveur l'a refusé ou les essais sont épuisés ; l'utilisateur peut réessayer. */
    Echec,
}

/** Statut affiché sous une bulle (EF-MSG-07), du plus faible au plus fort après l'envoi. */
enum class StatutMessage { EnAttente, Echec, Envoye, Recu, Lu }

/** Accusés d'un destinataire pour un message sortant. Une date nulle : pas encore. */
data class Accuse(
    val destinataire: IdMembre,
    val recuLe: Instant? = null,
    val luLe: Instant? = null,
)

/** Réaction d'un membre (une seule par membre et par message, EF-MSG-08). */
data class Reaction(val membre: IdMembre, val emoji: String)

/**
 * Un message de la conversation.
 *
 * @param sortant écrit sur ce téléphone.
 * @param contenu nul quand le message a été supprimé pour les deux (EF-MSG-09) : il ne reste que
 *   la mention « Message supprimé ».
 * @param reponseA message cité (EF-MSG-08). Il peut manquer chez le destinataire (supprimé pour
 *   lui, ou pas encore arrivé) : l'écran affiche alors « Message indisponible ».
 * @param creeLe heure de l'auteur au moment de l'écriture.
 * @param arriveLe heure où ce téléphone l'a enregistré (égale à [creeLe] pour un message sortant).
 * @param etatEnvoi pour un message reçu, toujours [EtatEnvoi.Envoye].
 * @param accuses un par destinataire pour un message sortant, vide pour un message reçu.
 * @param masque supprimé pour moi seulement : gardé comme trace pour qu'un renvoi ne le fasse
 *   pas réapparaître, mais jamais affiché.
 */
data class Message(
    val id: IdMessage,
    val conversation: IdConversation,
    val auteur: IdMembre,
    val sortant: Boolean,
    val contenu: ContenuMessage?,
    val reponseA: IdMessage?,
    val creeLe: Instant,
    val arriveLe: Instant,
    val etatEnvoi: EtatEnvoi,
    val accuses: List<Accuse> = emptyList(),
    val reactions: List<Reaction> = emptyList(),
    val supprimePourTous: Boolean = false,
    val masque: Boolean = false,
)

/** Message arrivé d'un autre téléphone, déjà déchiffré. */
data class MessageEntrant(
    val id: IdMessage,
    val conversation: IdConversation,
    val auteur: IdMembre,
    val texte: String,
    val reponseA: IdMessage?,
    val creeLe: Instant,
)

enum class TypeAccuse { Recu, Lu }

/** Pourquoi une opération sur les messages est refusée. */
sealed interface ErreurMessagerie {
    data object TexteVide : ErreurMessagerie
    data class TexteTropLong(val maximum: Int) : ErreurMessagerie
    data object AucunDestinataire : ErreurMessagerie
    data object MessageInconnu : ErreurMessagerie

    /** Le message cité n'existe pas, est supprimé ou appartient à une autre conversation. */
    data object CitationImpossible : ErreurMessagerie

    /** Seul l'auteur peut supprimer un message pour les deux. */
    data object PasAuteur : ErreurMessagerie
    data object DejaSupprime : ErreurMessagerie
    data object ReactionInvalide : ErreurMessagerie

    /** Un accusé vient d'un membre à qui le message n'était pas destiné, ou porte sur un message reçu. */
    data object AccuseInattendu : ErreurMessagerie

    /** Un message reçu porte l'identifiant d'un message existant d'un autre auteur ou d'une autre conversation. */
    data object Incoherent : ErreurMessagerie

    /** L'opération demande un autre état d'envoi (réessayer un message déjà parti, par exemple). */
    data object EtatInattendu : ErreurMessagerie
}
