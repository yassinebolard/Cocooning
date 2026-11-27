package fr.cocooning.serveur

import fr.cocooning.protocole.api.ContenuSigne
import io.ktor.http.HttpStatusCode
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Erreur métier traduite en réponse HTTP avec un code stable. */
class ErreurServeur(val statut: HttpStatusCode, val code: String, message: String, val detail: Any? = null) :
    Exception(message)

/** Appareil authentifié par un jeton. */
data class Appelant(val appareil: String, val membre: String, val espace: String, val numero: Int)

internal val aleatoire = SecureRandom()

internal fun octetsAleatoires(n: Int) = ByteArray(n).also(aleatoire::nextBytes)

internal fun sha256(octets: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(octets)

/**
 * Authentification par appareil (ADR 0004) : chaque téléphone a une paire EC P-256 dont la clé
 * privée reste dans l'Android Keystore. Il signe un défi à usage unique et reçoit un jeton court
 * (15 minutes, ENF-SEC-06). Le serveur ne garde aucun secret capable d'ouvrir une session : la
 * base ne contient que des clés publiques, et les jetons, en mémoire, n'y sont que hachés.
 * Un redémarrage du serveur invalide les jetons ; les téléphones en redemandent un, sans rien
 * demander à l'utilisateur.
 */
class Authentification(private val config: ConfigServeur, private val horloge: Clock) {

    private class Attente(val appareil: String, val expireLe: Long)

    private val defis = ConcurrentHashMap<String, Attente>()
    private val jetons = ConcurrentHashMap<String, Pair<Appelant, Long>>()

    fun nouveauDefi(appareil: String): Pair<ByteArray, Long> {
        val defi = octetsAleatoires(32)
        val expire = horloge.millis() + config.validiteDefi.toMillis()
        defis[cle(defi)] = Attente(appareil, expire)
        return defi to expire
    }

    /**
     * Vérifie la signature du défi par la clé de l'appareil. Le défi est consommé dans tous les cas,
     * réussite ou échec : il ne sert qu'une fois.
     */
    fun delivrerJeton(appelant: Appelant, cleAuth: ByteArray, defi: ByteArray, signature: ByteArray): Pair<String, Long> {
        val attente = defis.remove(cle(defi))
        val maintenant = horloge.millis()
        if (attente == null || attente.appareil != appelant.appareil || attente.expireLe < maintenant) {
            throw ErreurServeur(HttpStatusCode.Unauthorized, "defi_invalide", "Défi inconnu, expiré ou déjà utilisé")
        }
        if (!signatureValide(cleAuth, ContenuSigne.pour(appelant.appareil, defi), signature)) {
            throw ErreurServeur(HttpStatusCode.Unauthorized, "signature_invalide", "Signature refusée")
        }
        val jeton = Base64.getUrlEncoder().withoutPadding().encodeToString(octetsAleatoires(32))
        val expire = maintenant + config.validiteJeton.toMillis()
        jetons[cle(jeton.toByteArray())] = appelant to expire
        return jeton to expire
    }

    fun verifier(jeton: String): Appelant? {
        val (appelant, expire) = jetons[cle(jeton.toByteArray())] ?: return null
        return if (expire >= horloge.millis()) appelant else null
    }

    /** Retire tous les jetons d'un appareil (téléphone remplacé). */
    fun revoquer(appareil: String) {
        jetons.entries.removeIf { it.value.first.appareil == appareil }
        defis.entries.removeIf { it.value.appareil == appareil }
    }

    fun purger() {
        val maintenant = horloge.millis()
        jetons.entries.removeIf { it.value.second < maintenant }
        defis.entries.removeIf { it.value.expireLe < maintenant }
    }

    private fun cle(octets: ByteArray) = Base64.getEncoder().encodeToString(sha256(octets))

    companion object {
        /** Clé publique EC P-256 au format X.509 ; refuse toute autre courbe ou tout autre type. */
        fun lireCleAuth(octets: ByteArray): PublicKey {
            val cle = try {
                KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(octets))
            } catch (e: Exception) {
                throw ErreurServeur(HttpStatusCode.BadRequest, "cle_invalide", "Clé d'authentification illisible")
            }
            if (cle !is ECPublicKey || cle.params.curve.field.fieldSize != 256) {
                throw ErreurServeur(HttpStatusCode.BadRequest, "cle_invalide", "Clé EC P-256 attendue")
            }
            return cle
        }

        fun signatureValide(cleAuth: ByteArray, contenu: ByteArray, signature: ByteArray): Boolean = try {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(lireCleAuth(cleAuth))
                update(contenu)
                verify(signature)
            }
        } catch (e: Exception) {
            false
        }
    }
}
