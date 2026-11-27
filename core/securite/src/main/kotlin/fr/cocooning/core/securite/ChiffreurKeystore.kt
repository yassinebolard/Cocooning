package fr.cocooning.core.securite

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Chiffre et déchiffre de petits blocs d'octets ; l'implémentation réelle est [ChiffreurKeystore]. */
internal interface Chiffreur {
    /** Rend une [Enveloppe] sérialisée. [contexte] est authentifié mais pas chiffré (AAD GCM). */
    fun chiffrer(clair: ByteArray, contexte: ByteArray? = null): ByteArray

    /** Lève une exception si l'enveloppe est illisible, modifiée, ou si la clé a disparu. */
    fun dechiffrer(enveloppe: ByteArray, contexte: ByteArray? = null): ByteArray
}

/**
 * AES-256-GCM avec une clé de l'Android Keystore (ENF-SEC-02), adossé au matériel sur le S25+ :
 * la clé ne sort jamais du téléphone. Une clé par usage, désignée par [alias].
 */
internal class ChiffreurKeystore(private val alias: String) : Chiffreur {

    override fun chiffrer(clair: ByteArray, contexte: ByteArray?): ByteArray {
        val chiffreur = Cipher.getInstance(TRANSFORMATION)
        chiffreur.init(Cipher.ENCRYPT_MODE, cleOuNouvelle())
        contexte?.let(chiffreur::updateAAD)
        return Enveloppe(chiffreur.iv, chiffreur.doFinal(clair)).versOctets()
    }

    override fun dechiffrer(enveloppe: ByteArray, contexte: ByteArray?): ByteArray {
        val lue = Enveloppe.depuisOctets(enveloppe)
        val cle = cleExistante() ?: error("Clé du Keystore absente")
        val dechiffreur = Cipher.getInstance(TRANSFORMATION)
        dechiffreur.init(Cipher.DECRYPT_MODE, cle, GCMParameterSpec(TAILLE_TAG_BITS, lue.iv))
        contexte?.let(dechiffreur::updateAAD)
        return dechiffreur.doFinal(lue.contenu)
    }

    private fun cleExistante(): SecretKey? {
        val keystore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return keystore.getKey(alias, null) as? SecretKey
    }

    @Synchronized
    private fun cleOuNouvelle(): SecretKey = cleExistante() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }
        .generateKey()

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAILLE_TAG_BITS = 128
    }
}

/**
 * Format d'une enveloppe écrite sur le disque : un octet de version, la longueur du vecteur
 * d'initialisation, le vecteur, puis le contenu chiffré (avec son étiquette GCM).
 */
internal class Enveloppe(val iv: ByteArray, val contenu: ByteArray) {

    fun versOctets(): ByteArray = byteArrayOf(VERSION, iv.size.toByte()) + iv + contenu

    companion object {
        const val VERSION: Byte = 1

        fun depuisOctets(octets: ByteArray): Enveloppe {
            require(octets.size > 2 && octets[0] == VERSION) { "Enveloppe illisible" }
            val tailleIv = octets[1].toInt()
            require(tailleIv in 1..32 && octets.size > 2 + tailleIv) { "Enveloppe tronquée" }
            return Enveloppe(octets.copyOfRange(2, 2 + tailleIv), octets.copyOfRange(2 + tailleIv, octets.size))
        }
    }
}
