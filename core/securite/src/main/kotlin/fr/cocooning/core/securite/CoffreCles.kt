package fr.cocooning.core.securite

import android.util.AtomicFile
import java.io.File
import java.security.SecureRandom

/**
 * Phrases de passe des bases locales, une par base (ENF-SEC-02, ENF-SEC-04).
 *
 * Chaque phrase est tirée au hasard (256 bits), puis enveloppée par une clé AES-GCM qui vit dans
 * l'Android Keystore, adossé au matériel sur le S25+ : cette clé ne sort jamais du téléphone.
 * Seule l'enveloppe est écrite, dans `no_backup/cles/`, que les sauvegardes Android ignorent.
 *
 * Copier l'enveloppe sur un autre téléphone ne sert à rien : la clé du Keystore n'y est pas.
 * Le changement de téléphone passe par la sauvegarde chiffrée de EF-CPT-08 ([fr.cocooning.core.securite.sauvegarde.SauvegardeCocooning]).
 */
internal class CoffreCles(
    private val dossier: File,
    private val chiffreur: Chiffreur = ChiffreurKeystore(ALIAS),
) {

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
    fun creer(nomBase: String): ByteArray = PhraseDePasse.nouvelle(SecureRandom()).also { enregistrer(nomBase, it) }

    /**
     * Enregistre [phrase] pour [nomBase], enveloppée par la clé du Keystore de ce téléphone.
     * Sert aussi à la restauration d'une sauvegarde : la phrase vient alors de l'ancien téléphone.
     */
    fun enregistrer(nomBase: String, phrase: ByteArray) {
        val fichier = AtomicFile(fichier(nomBase).also { it.parentFile?.mkdirs() })
        val flux = fichier.startWrite()
        try {
            flux.write(envelopper(phrase))
            fichier.finishWrite(flux)
        } catch (e: Exception) {
            fichier.failWrite(flux)
            throw e
        }
    }

    private fun fichier(nomBase: String) = File(dossier, "$nomBase$SUFFIXE")

    private fun envelopper(phrase: ByteArray): ByteArray = chiffreur.chiffrer(phrase)

    private fun ouvrir(octets: ByteArray): ByteArray = chiffreur.dechiffrer(octets)

    companion object {
        /** Alias historique de la clé du Keystore : ne pas changer, les enveloppes existantes en dépendent. */
        const val ALIAS = "cocooning_bases_locales"
        private const val SUFFIXE = ".cle"
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
