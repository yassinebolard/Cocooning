package fr.cocooning.core.chiffrement

/*
 * Types publics du chiffrement de bout en bout (ADR 0003). Aucun type libsignal n'en sort : le
 * reste de l'application ne manipule que des octets, sérialisables tels quels vers le serveur.
 */

/** Un téléphone d'un membre. En V1, chaque membre a un seul appareil, le numéro 1. */
data class AdresseAppareil(val membre: String, val appareil: Int = 1) {
    init {
        require(membre.isNotBlank()) { "Membre vide" }
        require(appareil in 1..127) { "Numéro d'appareil hors de 1 à 127 : $appareil" }
    }
}

/** Clé publique d'une pré-clé, signée par l'identité pour les pré-clés signées et Kyber. */
class PreClePublique(val id: Int, val cle: ByteArray, val signature: ByteArray? = null)

/**
 * Ce que ce téléphone publie sur le serveur pour qu'on puisse lui écrire sans qu'il soit en
 * ligne : son identité publique, une pré-clé signée, une pré-clé post-quantique (Kyber) de
 * dernier recours, et des pré-clés à usage unique.
 *
 * @param preClesUniques seulement les nouvelles, à ajouter à celles déjà publiées.
 */
class PublicationCles(
    val idEnregistrement: Int,
    val appareil: Int,
    val identite: ByteArray,
    val preCleSignee: PreClePublique,
    val preCleKyber: PreClePublique,
    val preClesUniques: List<PreClePublique>,
)

/**
 * Ce que le serveur renvoie pour écrire à un appareil la première fois. Il donne une pré-clé à
 * usage unique tant qu'il en a, puis plus aucune : la session s'ouvre quand même.
 */
class PaquetDestinataire(
    val idEnregistrement: Int,
    val appareil: Int,
    val identite: ByteArray,
    val preCleSignee: PreClePublique,
    val preCleKyber: PreClePublique,
    val preCleUnique: PreClePublique?,
)

enum class TypeEnveloppe {
    /** Premier message d'une session : il porte de quoi l'ouvrir chez le destinataire. */
    OuvertureSession,

    /** Message d'une session déjà ouverte. */
    Message,
}

/** Message chiffré pour un appareil, tel qu'il part sur le réseau. */
class EnveloppeChiffree(val type: TypeEnveloppe, val octets: ByteArray)

/** Ce que l'on sait de l'identité d'un appareil après un échange. */
enum class ChangementIdentite {
    /** Déjà connue, et identique. */
    Inchangee,

    /** Vue pour la première fois. */
    Nouvelle,

    /** Différente de celle retenue : nouveau téléphone, ou attaque. Le code de sécurité a changé. */
    Changee,
}

class MessageDechiffre(val clair: ByteArray, val identite: ChangementIdentite)

/**
 * Code de sécurité à comparer entre les deux téléphones (EF-CPT-04).
 *
 * @param chiffres 60 chiffres en 12 groupes de 5 séparés par une espace, identiques des deux côtés.
 * @param qr contenu du QR code à faire scanner par l'autre téléphone.
 */
class CodeSecurite(val chiffres: String, val qr: ByteArray)

class ExceptionChiffrement(val raison: Raison, cause: Throwable? = null) :
    Exception("Chiffrement : $raison", cause) {

    enum class Raison {
        /** Ce téléphone n'a pas encore créé son identité. */
        IdentiteAbsente,

        /** Aucune session avec cet appareil : il faut d'abord récupérer son paquet de clés. */
        PasDeSession,

        /** Message déjà déchiffré une fois (renvoi) : à ignorer sans alerte. */
        MessageEnDouble,

        /** Message abîmé, falsifié ou incompréhensible. */
        MessageInvalide,

        /** Paquet de clés publiques incohérent ou mal signé. */
        PaquetInvalide,
    }
}
