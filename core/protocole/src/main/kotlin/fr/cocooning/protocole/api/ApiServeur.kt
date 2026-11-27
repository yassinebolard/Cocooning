package fr.cocooning.protocole.api

import fr.cocooning.protocole.Octets
import kotlinx.serialization.Serializable

/*
 * Requêtes et réponses JSON du serveur Cocooning, version 1 (préfixe /v1). Le serveur ne lit
 * jamais un contenu : messages, médias et clés privées n'y passent que chiffrés ou pas du tout.
 * Les heures sont en millisecondes depuis le 1er janvier 1970 (UTC). Voir docs/adr/0004.
 */

object CheminsApi {
    const val ESPACES = "/v1/espaces"
    const val REJOINDRE = "/v1/espaces/rejoindre"
    const val INVITATIONS = "/v1/espaces/invitations"
    const val ESPACE = "/v1/espace"
    const val REMPLACER_APPAREIL = "/v1/appareils/remplacer"
    const val REVEIL = "/v1/appareils/moi/reveil"
    const val DEFI = "/v1/auth/defi"
    const val JETON = "/v1/auth/jeton"
    const val CLES = "/v1/cles"
    const val ETAT_CLES = "/v1/cles/etat"
    fun clesMembre(membre: String) = "/v1/membres/$membre/cles"
    const val MESSAGES = "/v1/messages"
    const val ACQUITTER = "/v1/messages/acquitter"
    const val MEDIAS = "/v1/medias"
    fun media(id: String) = "/v1/medias/$id"
    fun morceau(id: String, numero: Int) = "/v1/medias/$id/morceaux/$numero"
    fun terminerMedia(id: String) = "/v1/medias/$id/terminer"
    fun mediaRecu(id: String) = "/v1/medias/$id/recu"
    const val TEMPS_REEL = "/v1/temps-reel"

    /** Texte signé par l'appareil pour obtenir un jeton (séparation des usages de la clé). */
    const val CONTEXTE_SIGNATURE = "cocooning-auth-v1"
}

/**
 * Erreur renvoyée avec un statut HTTP 4xx ou 5xx. [code] est stable, [message] est lisible.
 * [appareils] accompagne le code `appareils_incoherents` (409) d'un envoi de message.
 */
@Serializable
data class ErreurApi(val code: String, val message: String, val appareils: AppareilsIncoherents? = null)

// Inscription et appareils

/**
 * Création d'un espace par son premier membre (EF-CPT-02).
 *
 * @param cleAuth clé publique d'authentification de l'appareil (EC P-256, format X.509), dont la
 *   clé privée reste dans l'Android Keystore.
 * @param empreinteRecuperation SHA-256 du secret de récupération rangé dans la sauvegarde
 *   (EF-CPT-08) : il permettra à un nouveau téléphone de remplacer celui-ci.
 */
@Serializable
class CreationEspace(val cleAuth: Octets, val empreinteRecuperation: Octets)

/** Arrivée d'un membre avec un code d'invitation (EF-CPT-03). */
@Serializable
class RejoindreEspace(val code: String, val cleAuth: Octets, val empreinteRecuperation: Octets)

/** Nouveau téléphone d'un membre, prouvé par le secret de récupération (EF-CPT-09). */
@Serializable
class RemplacerAppareil(
    val membre: String,
    val secretRecuperation: Octets,
    val cleAuth: Octets,
    val empreinteRecuperation: Octets,
)

@Serializable
data class Invitation(val code: String, val expireLe: Long)

/**
 * @param numero numéro Signal de l'appareil pour son membre (1 à 127).
 * @param invitation code à donner à l'autre membre, seulement à la création de l'espace.
 */
@Serializable
data class Inscription(
    val espace: String,
    val membre: String,
    val appareil: String,
    val numero: Int,
    val invitation: Invitation? = null,
)

@Serializable
data class AppareilApi(val id: String, val numero: Int)

@Serializable
data class MembreApi(val id: String, val appareils: List<AppareilApi>)

/** Membres de l'espace et leurs appareils actifs : ce sont les destinataires d'un message. */
@Serializable
data class EspaceApi(val id: String, val membres: List<MembreApi>)

/** Voie de réveil de l'appareil (ENF-EVO-03). La notification ne porte aucun contenu (ENF-SEC-07). */
@Serializable
data class ReveilApi(val type: TypeReveil, val cible: String = "")

@Serializable
enum class TypeReveil { Aucun, Fcm, Ntfy }

// Authentification

@Serializable
data class DemandeDefi(val appareil: String)

@Serializable
class Defi(val defi: Octets, val expireLe: Long)

/**
 * @param signature SHA256withECDSA, par la clé d'authentification, de
 *   [CheminsApi.CONTEXTE_SIGNATURE], de l'identifiant d'appareil puis du défi (voir `ContenuSigne`).
 */
@Serializable
class DemandeJeton(val appareil: String, val defi: Octets, val signature: Octets)

/** Jeton d'accès court (ENF-SEC-06), à envoyer dans `Authorization: Bearer`. */
@Serializable
data class Jeton(val jeton: String, val expireLe: Long)

/** Ce qu'un appareil signe pour s'authentifier : sans ambiguïté possible entre les champs. */
object ContenuSigne {
    fun pour(appareil: String, defi: ByteArray): ByteArray {
        val contexte = CheminsApi.CONTEXTE_SIGNATURE.toByteArray(Charsets.UTF_8)
        val id = appareil.toByteArray(Charsets.UTF_8)
        return java.nio.ByteBuffer.allocate(4 + contexte.size + 4 + id.size + defi.size)
            .putInt(contexte.size).put(contexte).putInt(id.size).put(id).put(defi).array()
    }
}

// Clés publiques Signal

@Serializable
class PreCleApi(val id: Int, val cle: Octets, val signature: Octets? = null)

/** Publication des clés publiques d'un appareil (correspond à `PublicationCles` du chiffrement). */
@Serializable
class PublicationClesApi(
    val idEnregistrement: Int,
    val identite: Octets,
    val preCleSignee: PreCleApi,
    val preCleKyber: PreCleApi,
    val preClesUniques: List<PreCleApi>,
)

@Serializable
data class EtatCles(val preClesUniquesRestantes: Int)

/** Paquet pour ouvrir une session avec un appareil (correspond à `PaquetDestinataire`). */
@Serializable
class PaquetApi(
    val appareil: String,
    val numero: Int,
    val idEnregistrement: Int,
    val identite: Octets,
    val preCleSignee: PreCleApi,
    val preCleKyber: PreCleApi,
    val preCleUnique: PreCleApi? = null,
)

@Serializable
class PaquetsMembre(val membre: String, val paquets: List<PaquetApi>)

// Messages

/** Type Signal de l'enveloppe : le serveur le transmet sans le comprendre. */
@Serializable
enum class TypeEnveloppeApi { OuvertureSession, Message }

@Serializable
class EnvoiAppareil(val appareil: String, val type: TypeEnveloppeApi, val contenu: Octets)

/**
 * Un message chiffré une fois par appareil destinataire (ADR 0003, section 4). Pour chaque membre
 * visé, tous ses appareils actifs doivent être servis ; sinon le serveur répond 409 avec
 * [AppareilsIncoherents] pour que l'appli mette à jour sa liste.
 *
 * @param id identifiant créé par le téléphone : un renvoi avec le même id ne crée pas de doublon.
 */
@Serializable
class EnvoiMessage(val id: String, val envois: List<EnvoiAppareil>)

@Serializable
data class AppareilsIncoherents(val manquants: List<String>, val enTrop: List<String>)

/** Le serveur a accepté le message : c'est le statut « envoyé ». */
@Serializable
data class AccuseDepot(val id: String, val deposeLe: Long)

@Serializable
class MessageRecu(
    val seq: Long,
    val id: String,
    val expediteurMembre: String,
    val expediteurAppareil: String,
    val expediteurNumero: Int,
    val type: TypeEnveloppeApi,
    val contenu: Octets,
    val deposeLe: Long,
)

/** @param suite vrai s'il reste des messages après ceux-ci. */
@Serializable
class Releve(val messages: List<MessageRecu>, val suite: Boolean)

/** Messages bien enregistrés sur le téléphone : le serveur les efface. */
@Serializable
data class Acquittement(val seqs: List<Long>)

// Médias

@Serializable
data class CreationMedia(val taille: Long)

/** Média chiffré à envoyer en [nombreMorceaux] morceaux de [tailleMorceau] octets (le dernier plus court). */
@Serializable
data class MediaCree(val id: String, val tailleMorceau: Int, val nombreMorceaux: Int)

@Serializable
data class EtatMedia(
    val id: String,
    val taille: Long,
    val tailleMorceau: Int,
    val morceauxRecus: List<Int>,
    val termine: Boolean,
)

// Temps réel (WebSocket)

/** Trames JSON de la WebSocket. */
@Serializable
sealed interface TrameTempsReel {
    /** Serveur vers appareil : de nouveaux messages attendent dans la boîte. */
    @Serializable
    @kotlinx.serialization.SerialName("nouveaux")
    data object Nouveaux : TrameTempsReel

    /**
     * Éphémère chiffré (« en train d'écrire ») : de l'appareil vers le serveur avec [appareil]
     * destinataire, du serveur vers le destinataire avec [appareil] expéditeur. Jamais stocké.
     */
    @Serializable
    @kotlinx.serialization.SerialName("ephemere")
    class Ephemere(
        val appareil: String,
        val contenu: Octets,
        val type: TypeEnveloppeApi = TypeEnveloppeApi.Message,
    ) : TrameTempsReel
}
