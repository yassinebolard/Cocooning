package fr.cocooning.core.reseau

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Clé d'authentification de ce téléphone auprès du serveur (ADR 0004) : EC P-256, signature
 * SHA256withECDSA. Elle n'a rien à voir avec les clés Signal, qui chiffrent les messages.
 */
interface CleAppareil {
    fun existe(): Boolean

    /** Crée la clé si elle n'existe pas ; renvoie la clé publique au format X.509. */
    fun creer(): ByteArray

    fun clePublique(): ByteArray
    fun signer(contenu: ByteArray): ByteArray
    fun supprimer()
}

/**
 * Clé rangée dans l'Android Keystore (StrongBox si le téléphone en a, comme le S25+) : la clé
 * privée ne sort jamais du matériel (ENF-SEC-02). Elle n'est pas dans la sauvegarde : sur un
 * nouveau téléphone, une nouvelle clé est créée et le secret de récupération fait le lien.
 */
class CleAppareilKeystore(private val alias: String = ALIAS) : CleAppareil {

    private val magasin: KeyStore get() = KeyStore.getInstance(FOURNISSEUR).apply { load(null) }

    override fun existe(): Boolean = magasin.containsAlias(alias)

    @Synchronized
    override fun creer(): ByteArray {
        if (!existe()) {
            try {
                generer(strongBox = true)
            } catch (e: StrongBoxUnavailableException) {
                generer(strongBox = false)
            }
        }
        return clePublique()
    }

    override fun clePublique(): ByteArray = checkNotNull(magasin.getCertificate(alias)) { "Clé d'appareil absente" }.publicKey.encoded

    override fun signer(contenu: ByteArray): ByteArray {
        val privee = magasin.getKey(alias, null) as? PrivateKey ?: error("Clé d'appareil absente")
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(privee)
            update(contenu)
            sign()
        }
    }

    override fun supprimer() {
        if (existe()) magasin.deleteEntry(alias)
    }

    private fun generer(strongBox: Boolean): KeyPair {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setIsStrongBoxBacked(strongBox)
            .build()
        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, FOURNISSEUR).run {
            initialize(spec)
            generateKeyPair()
        }
    }

    companion object {
        const val ALIAS = "cocooning_appareil"
        private const val FOURNISSEUR = "AndroidKeyStore"
    }
}

/** Clé gardée en mémoire, pour les tests sur la JVM. */
class CleAppareilMemoire : CleAppareil {
    private var paire: KeyPair? = null

    override fun existe() = paire != null

    override fun creer(): ByteArray {
        if (paire == null) {
            paire = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        }
        return clePublique()
    }

    override fun clePublique(): ByteArray = checkNotNull(paire) { "Clé d'appareil absente" }.public.encoded

    override fun signer(contenu: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(checkNotNull(paire) { "Clé d'appareil absente" }.private)
        update(contenu)
        sign()
    }

    override fun supprimer() {
        paire = null
    }
}
