package fr.cocooning.protocole.contenu

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

/*
 * Contenu en clair d'un message, avant le chiffrement Signal (ADR 0003, section 4) : c'est ce
 * que :core:messagerie produit et que :core:chiffrement chiffre, octet pour octet.
 *
 * Protocol Buffers : compact, et extensible. Un champ ajouté plus tard avec un nouveau numéro est
 * ignoré par une ancienne version de l'appli, qui voit alors un contenu inconnu au lieu de planter.
 * Ne jamais réutiliser ni renuméroter un numéro de champ.
 *
 * L'auteur n'est pas dans le contenu : c'est l'appareil dont la session Signal a déchiffré le
 * message, ce qu'un expéditeur ne peut pas falsifier.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ContenuClair(
    @ProtoNumber(1) val version: Int = VERSION,
    /** Identifiant du message (UUID créé par l'auteur), même pour un accusé ou une réaction. */
    @ProtoNumber(2) val id: String,
    @ProtoNumber(3) val conversation: String,
    /** Heure de l'auteur, en millisecondes depuis 1970 (UTC). */
    @ProtoNumber(4) val creeLe: Long,
    /** Message cité (EF-MSG-08), pour un texte. */
    @ProtoNumber(5) val reponseA: String? = null,
    // Un seul des champs suivants est rempli.
    @ProtoNumber(10) val texte: Texte? = null,
    @ProtoNumber(11) val reaction: Reaction? = null,
    @ProtoNumber(12) val suppression: Suppression? = null,
    @ProtoNumber(13) val accuse: Accuse? = null,
    @ProtoNumber(14) val ecriture: Ecriture? = null,
    // 15 et suivants : pièces jointes (vocal, image, GIF, vidéo), à venir.
) {
    @Serializable
    data class Texte(@ProtoNumber(1) val corps: String)

    /** @param emoji nul pour retirer sa réaction. */
    @Serializable
    data class Reaction(@ProtoNumber(1) val cible: String, @ProtoNumber(2) val emoji: String? = null)

    /** Suppression pour tous d'un message de l'auteur (EF-MSG-09). */
    @Serializable
    data class Suppression(@ProtoNumber(1) val cible: String)

    /** Accusé de réception ou de lecture de plusieurs messages d'un coup (EF-MSG-07). */
    @Serializable
    data class Accuse(@ProtoNumber(1) val type: TypeAccuse, @ProtoNumber(2) val cibles: List<String>)

    @Serializable
    enum class TypeAccuse {
        @ProtoNumber(0) Recu,
        @ProtoNumber(1) Lu,
    }

    /** « En train d'écrire » : éphémère, envoyé par la WebSocket seulement. */
    @Serializable
    data class Ecriture(@ProtoNumber(1) val enCours: Boolean)

    companion object {
        const val VERSION = 1
    }
}

/** Ce qu'un contenu décodé représente. */
sealed interface Contenu {
    val clair: ContenuClair

    data class Texte(override val clair: ContenuClair, val corps: String) : Contenu
    data class Reaction(override val clair: ContenuClair, val cible: String, val emoji: String?) : Contenu
    data class Suppression(override val clair: ContenuClair, val cible: String) : Contenu
    data class Accuse(override val clair: ContenuClair, val type: ContenuClair.TypeAccuse, val cibles: List<String>) : Contenu
    data class Ecriture(override val clair: ContenuClair, val enCours: Boolean) : Contenu

    /** Type venu d'une version plus récente de l'appli : à ignorer, sans erreur. */
    data class Inconnu(override val clair: ContenuClair) : Contenu
}

@OptIn(ExperimentalSerializationApi::class)
object FormatContenu {
    private val protobuf = ProtoBuf

    fun encoder(contenu: ContenuClair): ByteArray {
        require(contenu.remplis() == 1) { "Un contenu porte exactement un type : ${contenu.remplis()} trouvés" }
        return protobuf.encodeToByteArray(contenu)
    }

    /**
     * Décode un contenu déchiffré. Lève [ContenuInvalide] si les octets ne sont pas un contenu,
     * s'il en porte plusieurs à la fois, ou s'il manque l'identifiant ou la conversation.
     */
    fun decoder(octets: ByteArray): Contenu {
        val clair = try {
            protobuf.decodeFromByteArray<ContenuClair>(octets)
        } catch (e: SerializationException) {
            throw ContenuInvalide("Contenu illisible", e)
        } catch (e: IllegalArgumentException) {
            throw ContenuInvalide("Contenu illisible", e)
        }
        if (clair.id.isBlank() || clair.conversation.isBlank()) throw ContenuInvalide("Identifiant ou conversation vide")
        return when {
            clair.remplis() > 1 -> throw ContenuInvalide("Plusieurs types dans un même contenu")
            clair.texte != null -> Contenu.Texte(clair, clair.texte.corps)
            clair.reaction != null -> Contenu.Reaction(clair, clair.reaction.cible, clair.reaction.emoji)
            clair.suppression != null -> Contenu.Suppression(clair, clair.suppression.cible)
            clair.accuse != null -> Contenu.Accuse(clair, clair.accuse.type, clair.accuse.cibles)
            clair.ecriture != null -> Contenu.Ecriture(clair, clair.ecriture.enCours)
            else -> Contenu.Inconnu(clair)
        }
    }

    private fun ContenuClair.remplis() = listOfNotNull(texte, reaction, suppression, accuse, ecriture).size
}

class ContenuInvalide(message: String, cause: Throwable? = null) : Exception(message, cause)
