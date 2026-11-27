package fr.cocooning.core.securite

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Phrases de passe des bases locales, une par base (ENF-SEC-02, ENF-SEC-04).
 *
 * Chaque phrase est tirée au hasard (256 bits), puis enveloppée par une clé AES-GCM qui vit dans
 * l'Android Keystore, adossé au matériel sur le S25+ : cette clé ne sort jamais du téléphone.
 * Seule l'enveloppe est écrite, dans `no_backup/cles/`, que les sauvegardes Android ignorent.
 *
 * Copier l'enveloppe sur un autre téléphone ne sert à rien : la clé du Keystore n'y est pas.
 * Le changement de téléphone passera par la sauvegarde chiffrée de EF-CPT-08.
 */
internal class CoffreCles(private val dossier: File) {

    /** Phrase de passe existante de [nomBase], ou nulle si elle n'existe pas ou n'est plus lisible. */
    fun lire(nomBase: String): ByteArray? {
        val fichier = fichier(nomBase)
        if (!fichier.exists()) return null
        return try {
            ouvrir(AtomicFile(fichier).readFully())
        } catch (e: Exception) {
            // Clé du Keystore effacée (restauration, réinitialisation des données d'identification) :
            // l'enveloppe ne s'ouvre plus. L'appelant décide quoi faire de la base orpheline.
            null
        }
    }

    /** Crée et enregistre une nouvelle phrase de passe pour [nomBase], en remplaçant l'ancienne. */
    fun creer(nomBase: String): ByteArray {
        val phrase = PhraseDePasse.nouvelle(SecureRandom())
        val fichier = AtomicFile(fichier(nomBase).also { it.parentFile?.mkdirs() })
        val flux = fichier.startWrite()
        try {
            flux.write(envelopper(phrase))
            fichier.finishWrite(flux)
        } catch (e: Exception) {
            fichier.failWrite(flux)
            throw e
        }
        return phrase
    }

    private fun fichier(nomBase: String) = File(dossier, "$nomBase.cle")

    private fun envelopper(phrase: ByteArray): ByteArray {
        val chiffreur = Cipher.getInstance(TRANSFORMATION)
        chiffreur.init(Cipher.ENCRYPT_MODE, cleKeystore())
        return Enveloppe(chiffreur.iv, chiffreur.doFinal(phrase)).versOctets()
    }

    private fun ouvrir(octets: ByteArray): ByteArray {
        val enveloppe = Enveloppe.depuisOctets(octets)
        val cle = cleKeystoreExistante() ?: error("Clé du Keystore absente")
        val dechiffreur = Cipher.getInstance(TRANSFORMATION)
        dechiffreur.init(Cipher.DECRYPT_MODE, cle, GCMParameterSpec(TAILLE_TAG_BITS, enveloppe.iv))
        return dechiffreur.doFinal(enveloppe.contenu)
    }

    private fun cleKeystoreExistante(): SecretKey? {
        val keystore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return keystore.getKey(ALIAS, null) as? SecretKey
    }

    private fun cleKeystore(): SecretKey = cleKeystoreExistante() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }
        .generateKey()

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "cocooning_bases_locales"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAILLE_TAG_BITS = 128
    }
}

/**
 * Format de l'enveloppe écrite sur le disque : un octet de version, la longueur du vecteur
 * d'initialisation, le vecteur, puis la phrase chiffrée (avec son étiquette GCM).
 */
internal class Enveloppe(val iv: ByteArray, val contenu: ByteArray) {

    fun versOctets(): ByteArray = byteArrayOf(VERSION, iv.size.toByte()) + iv + contenu

    companion object {
        const val VERSION: Byte = 1

        fun depuisOctets(octets: ByteArray): Enveloppe {
            require(octets.size > 2 && octets[0] == VERSION) { "Enveloppe de clé illisible" }
            val tailleIv = octets[1].toInt()
            require(tailleIv in 1..32 && octets.size > 2 + tailleIv) { "Enveloppe de clé tronquée" }
            return Enveloppe(octets.copyOfRange(2, 2 + tailleIv), octets.copyOfRange(2 + tailleIv, octets.size))
        }
    }
}

/**
 * Phrase de passe d'une base : 32 octets aléatoires écrits en hexadécimal (64 caractères ASCII).
 * La forme texte permet de passer exactement la même phrase à SQLCipher par l'API et dans une
 * requête `ATTACH … KEY '…'` lors du chiffrement d'une ancienne base en clair.
 */
internal object PhraseDePasse {
    private const val OCTETS = 32

    fun nouvelle(aleatoire: SecureRandom): ByteArray {
        val brut = ByteArray(OCTETS).also(aleatoire::nextBytes)
        return brut.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.US_ASCII)
    }

    /** Vrai si [phrase] ne contient que des chiffres hexadécimaux : sûre dans une requête SQL. */
    fun estHexadecimale(phrase: ByteArray): Boolean =
        phrase.isNotEmpty() && phrase.all { it.toInt().toChar() in '0'..'9' || it.toInt().toChar() in 'a'..'f' }
}
