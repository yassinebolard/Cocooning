package fr.cocooning.core.securite.sauvegarde

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Coût de la dérivation Argon2id. Écrit dans chaque fichier, pour pouvoir le relever plus tard. */
data class ParametresArgon2(val memoireKio: Int, val iterations: Int, val parallelisme: Int) {
    companion object {
        /** 64 Mio, 3 passes : deuxième recommandation de la RFC 9106, de l'ordre d'une seconde sur le S25+. */
        val PAR_DEFAUT = ParametresArgon2(memoireKio = 64 * 1024, iterations = 3, parallelisme = 1)

        // Bornes à la lecture : un fichier piégé ne doit pas pouvoir épuiser la mémoire du téléphone.
        internal const val MEMOIRE_MAX_KIO = 256 * 1024
        internal const val ITERATIONS_MAX = 20
        internal const val PARALLELISME_MAX = 8
    }
}

/** Contenu en clair d'une sauvegarde : des entrées nommées (phrases de passe, bases, préférences). */
class ContenuSauvegarde(val entrees: Map<String, ByteArray>) {
    operator fun get(nom: String): ByteArray? = entrees[nom]
}

sealed class EchecOuverture(message: String) : Exception(message) {
    /** Mauvaise phrase, ou fichier modifié : GCM ne permet pas de distinguer les deux. */
    class PhraseIncorrecte : EchecOuverture("Phrase de récupération incorrecte ou fichier modifié")

    class FichierIllisible(raison: String) : EchecOuverture(raison)
}

/**
 * Fichier de sauvegarde chiffré par la phrase de récupération (EF-CPT-08).
 *
 * Clé AES-256 dérivée de la phrase normalisée par Argon2id (sel aléatoire de 16 octets), puis
 * AES-256-GCM sur le contenu. L'en-tête entier (format, paramètres, sel, nonce) est authentifié :
 * baisser le coût dans un fichier pour accélérer une attaque le rend illisible.
 *
 * Le même fichier servira à l'envoi quotidien vers le serveur Cocooning, qui ne pourra pas le lire.
 *
 * Format : `COCOSAV` + version (1) + algorithme (1 = Argon2id) + mémoire (Kio, int) + passes (int)
 * + parallélisme (octet) + sel (longueur, octets) + nonce (longueur, octets) + contenu chiffré.
 */
object SauvegardeChiffree {

    private val MAGIQUE = "COCOSAV".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val ALGO_ARGON2ID: Byte = 1
    private const val TAILLE_SEL = 16
    private const val TAILLE_NONCE = 12
    private const val TAILLE_CLE = 32
    private const val ENTREES_MAX = 1_000

    /**
     * Vrai si [debut] commence comme une sauvegarde Cocooning. Ne vérifie ni la phrase ni
     * l'intégrité : sert seulement à refuser tout de suite un fichier choisi par erreur.
     */
    fun estUneSauvegarde(debut: ByteArray): Boolean =
        debut.size >= MAGIQUE.size && debut.copyOfRange(0, MAGIQUE.size).contentEquals(MAGIQUE)

    fun sceller(
        contenu: ContenuSauvegarde,
        phrase: CharSequence,
        parametres: ParametresArgon2 = ParametresArgon2.PAR_DEFAUT,
        aleatoire: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(PhraseRecuperation.evaluer(phrase).acceptable) { "Phrase de récupération trop faible" }
        val sel = ByteArray(TAILLE_SEL).also(aleatoire::nextBytes)
        val nonce = ByteArray(TAILLE_NONCE).also(aleatoire::nextBytes)
        val entete = entete(parametres, sel, nonce)
        val cle = deriver(phrase, sel, parametres)
        try {
            val chiffreur = Cipher.getInstance("AES/GCM/NoPadding")
            chiffreur.init(Cipher.ENCRYPT_MODE, SecretKeySpec(cle, "AES"), GCMParameterSpec(128, nonce))
            chiffreur.updateAAD(entete)
            return entete + chiffreur.doFinal(serialiser(contenu))
        } finally {
            cle.fill(0)
        }
    }

    fun ouvrir(fichier: ByteArray, phrase: CharSequence): ContenuSauvegarde {
        val lecteur = DataInputStream(fichier.inputStream())
        val (parametres, sel, nonce) = try {
            lireEntete(lecteur)
        } catch (e: EOFException) {
            throw EchecOuverture.FichierIllisible("Fichier de sauvegarde tronqué")
        }
        val tailleEntete = fichier.size - lecteur.available()
        val entete = fichier.copyOfRange(0, tailleEntete)
        val chiffre = fichier.copyOfRange(tailleEntete, fichier.size)

        val cle = deriver(phrase, sel, parametres)
        val clair = try {
            val dechiffreur = Cipher.getInstance("AES/GCM/NoPadding")
            dechiffreur.init(Cipher.DECRYPT_MODE, SecretKeySpec(cle, "AES"), GCMParameterSpec(128, nonce))
            dechiffreur.updateAAD(entete)
            dechiffreur.doFinal(chiffre)
        } catch (e: AEADBadTagException) {
            throw EchecOuverture.PhraseIncorrecte()
        } finally {
            cle.fill(0)
        }
        return try {
            deserialiser(clair)
        } finally {
            clair.fill(0)
        }
    }

    internal fun deriver(phrase: CharSequence, sel: ByteArray, p: ParametresArgon2): ByteArray {
        val octets = PhraseRecuperation.normaliser(phrase).toByteArray(Charsets.UTF_8)
        val generateur = Argon2BytesGenerator()
        generateur.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(sel)
                .withMemoryAsKB(p.memoireKio)
                .withIterations(p.iterations)
                .withParallelism(p.parallelisme)
                .build(),
        )
        val cle = ByteArray(TAILLE_CLE)
        generateur.generateBytes(octets, cle)
        octets.fill(0)
        return cle
    }

    private fun entete(p: ParametresArgon2, sel: ByteArray, nonce: ByteArray): ByteArray {
        val flux = ByteArrayOutputStream()
        DataOutputStream(flux).apply {
            write(MAGIQUE)
            writeByte(VERSION.toInt())
            writeByte(ALGO_ARGON2ID.toInt())
            writeInt(p.memoireKio)
            writeInt(p.iterations)
            writeByte(p.parallelisme)
            writeByte(sel.size); write(sel)
            writeByte(nonce.size); write(nonce)
            flush()
        }
        return flux.toByteArray()
    }

    private fun lireEntete(e: DataInputStream): Triple<ParametresArgon2, ByteArray, ByteArray> {
        val magique = ByteArray(MAGIQUE.size).also(e::readFully)
        if (!magique.contentEquals(MAGIQUE)) throw EchecOuverture.FichierIllisible("Ce n'est pas une sauvegarde Cocooning")
        val version = e.readByte()
        if (version != VERSION) throw EchecOuverture.FichierIllisible("Sauvegarde d'une version plus récente de Cocooning")
        if (e.readByte() != ALGO_ARGON2ID) throw EchecOuverture.FichierIllisible("Dérivation de clé inconnue")
        val p = ParametresArgon2(memoireKio = e.readInt(), iterations = e.readInt(), parallelisme = e.readByte().toInt())
        val valides = p.memoireKio in 8 * p.parallelisme..ParametresArgon2.MEMOIRE_MAX_KIO &&
            p.iterations in 1..ParametresArgon2.ITERATIONS_MAX &&
            p.parallelisme in 1..ParametresArgon2.PARALLELISME_MAX
        if (!valides) throw EchecOuverture.FichierIllisible("Paramètres de dérivation hors bornes")
        val sel = ByteArray(e.readUnsignedByte()).also(e::readFully)
        val nonce = ByteArray(e.readUnsignedByte()).also(e::readFully)
        if (sel.size != TAILLE_SEL || nonce.size != TAILLE_NONCE) throw EchecOuverture.FichierIllisible("En-tête invalide")
        return Triple(p, sel, nonce)
    }

    private fun serialiser(contenu: ContenuSauvegarde): ByteArray {
        val flux = ByteArrayOutputStream()
        DataOutputStream(flux).apply {
            writeInt(contenu.entrees.size)
            contenu.entrees.toSortedMap().forEach { (nom, octets) ->
                writeUTF(nom)
                writeInt(octets.size)
                write(octets)
            }
            flush()
        }
        return flux.toByteArray()
    }

    private fun deserialiser(clair: ByteArray): ContenuSauvegarde {
        val e = DataInputStream(clair.inputStream())
        try {
            val nombre = e.readInt()
            if (nombre !in 0..ENTREES_MAX) throw EchecOuverture.FichierIllisible("Contenu invalide")
            val entrees = LinkedHashMap<String, ByteArray>()
            repeat(nombre) {
                val nom = e.readUTF()
                val taille = e.readInt()
                if (taille < 0 || taille > e.available()) throw EchecOuverture.FichierIllisible("Contenu invalide")
                entrees[nom] = ByteArray(taille).also(e::readFully)
            }
            return ContenuSauvegarde(entrees)
        } catch (ex: EOFException) {
            throw EchecOuverture.FichierIllisible("Contenu tronqué")
        }
    }
}
