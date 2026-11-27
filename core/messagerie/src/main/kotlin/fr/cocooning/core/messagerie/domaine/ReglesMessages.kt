package fr.cocooning.core.messagerie.domaine

import java.time.Instant

/** Règles des messages, sans état ni Android : tout ce qui décide se teste ici. */
object ReglesMessages {

    /** Longueur maximale d'un texte, en caractères (ADR 0003). À reprendre de la configuration (ENF-CONF-01). */
    const val LONGUEUR_MAX_PAR_DEFAUT = 10_000

    /** Une réaction est un emoji ; les séquences composées (familles, drapeaux) restent courtes. */
    private const val LONGUEUR_MAX_REACTION = 32

    /**
     * Texte tel qu'il sera enregistré : fins de ligne unifiées, espaces et lignes vides retirés
     * au début et à la fin. L'intérieur du texte n'est pas touché.
     */
    fun normaliser(brut: String): String = brut.replace("\r\n", "\n").replace('\r', '\n').trim()

    /** Vérifie un texte déjà normalisé. La longueur se compte en caractères Unicode, pas en unités UTF-16. */
    fun validerTexte(texte: String, longueurMax: Int = LONGUEUR_MAX_PAR_DEFAUT): ErreurMessagerie? = when {
        texte.isEmpty() -> ErreurMessagerie.TexteVide
        texte.codePointCount(0, texte.length) > longueurMax -> ErreurMessagerie.TexteTropLong(longueurMax)
        else -> null
    }

    /**
     * Une réaction non vide, courte, sans espace ni lettre. Pas de liste fermée d'emojis ; les
     * chiffres restent permis pour les touches numérotées (1️⃣).
     */
    fun validerReaction(emoji: String): ErreurMessagerie? = when {
        emoji.isEmpty() || emoji.length > LONGUEUR_MAX_REACTION -> ErreurMessagerie.ReactionInvalide
        emoji.any { it.isWhitespace() || it.isLetter() } -> ErreurMessagerie.ReactionInvalide
        else -> null
    }

    /**
     * Le message [cite] peut être cité dans [conversation] : il existe, il est de la même
     * conversation, et il n'est supprimé ni pour moi ni pour les deux.
     */
    fun peutCiter(cite: Message?, conversation: IdConversation): Boolean =
        cite != null && cite.conversation == conversation && !cite.masque && !cite.supprimePourTous

    /** Pourquoi [moi] ne peut pas supprimer [message] pour les deux, ou nul s'il le peut. */
    fun refusSuppressionPourTous(message: Message, moi: IdMembre): ErreurMessagerie? = when {
        message.auteur != moi -> ErreurMessagerie.PasAuteur
        message.supprimePourTous -> ErreurMessagerie.DejaSupprime
        else -> null
    }

    /**
     * Statut affiché. Pour plusieurs destinataires, c'est le plus faible : « lu » quand tous ont lu,
     * « reçu » quand tous ont reçu.
     */
    fun statut(message: Message): StatutMessage = when (message.etatEnvoi) {
        EtatEnvoi.EnAttente -> StatutMessage.EnAttente
        EtatEnvoi.Echec -> StatutMessage.Echec
        EtatEnvoi.Envoye -> when {
            message.accuses.isEmpty() -> StatutMessage.Envoye
            message.accuses.all { it.luLe != null } -> StatutMessage.Lu
            message.accuses.all { it.recuLe != null } -> StatutMessage.Recu
            else -> StatutMessage.Envoye
        }
    }

    /**
     * Ajoute un accusé sans jamais faire reculer le statut : la première date reçue est gardée,
     * un « reçu » arrivé après un « lu » ne change rien, et un « lu » vaut aussi « reçu ».
     */
    fun appliquerAccuse(accuse: Accuse, type: TypeAccuse, le: Instant): Accuse = when (type) {
        TypeAccuse.Recu -> accuse.copy(recuLe = accuse.recuLe ?: le)
        TypeAccuse.Lu -> accuse.copy(recuLe = accuse.recuLe ?: le, luLe = accuse.luLe ?: le)
    }

    /** Heure affichée : celle de l'auteur, sauf si son horloge avance (jamais dans le futur). */
    fun heureAffichee(message: Message): Instant = minOf(message.creeLe, message.arriveLe)

    /** Ordre d'affichage : par heure affichée, puis par identifiant pour un ordre stable. */
    val ordreAffichage: Comparator<Message> =
        compareBy<Message> { heureAffichee(it) }.thenBy { it.id.valeur }

    /** Messages visibles d'une conversation, dans l'ordre d'affichage. */
    fun visibles(messages: List<Message>): List<Message> = messages.filterNot { it.masque }.sortedWith(ordreAffichage)

    /** Pose, remplace ([emoji] non nul) ou retire ([emoji] nul) la réaction de [membre]. */
    fun reagir(reactions: List<Reaction>, membre: IdMembre, emoji: String?): List<Reaction> {
        val autres = reactions.filterNot { it.membre == membre }
        return if (emoji == null) autres else autres + Reaction(membre, emoji)
    }
}
