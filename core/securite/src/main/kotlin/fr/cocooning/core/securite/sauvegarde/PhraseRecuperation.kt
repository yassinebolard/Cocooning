package fr.cocooning.core.securite.sauvegarde

import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln
import kotlin.math.max

/**
 * Phrase de récupération choisie par l'utilisateur (EF-CPT-08, choix de Yassine du 2026-11-27).
 *
 * Elle protège un fichier qui peut tomber entre de mauvaises mains (Drive, PC, plus tard le
 * serveur) et être attaqué hors ligne sans limite d'essais. D'où une longueur minimale élevée,
 * un refus des phrases faibles et une dérivation lente (Argon2id, voir [SauvegardeChiffree]).
 */
object PhraseRecuperation {

    const val LONGUEUR_MINIMALE = 20

    /** En dessous, la phrase est refusée. */
    const val BITS_ACCEPTABLE = 45.0

    /** À partir de là, l'indicateur affiche « solide ». */
    const val BITS_SOLIDE = 60.0

    /**
     * Forme canonique, la seule qui sert à dériver la clé. Les majuscules, les espaces en trop et
     * les variantes Unicode d'un même caractère ne comptent pas : sur un nouveau téléphone, le
     * clavier met souvent une majuscule au premier mot ou compose « é » autrement.
     */
    fun normaliser(phrase: CharSequence): String =
        Normalizer.normalize(phrase, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .trim()
            .replace(Regex("\\s+"), " ")

    fun evaluer(phrase: CharSequence): Robustesse {
        val p = normaliser(phrase)
        if (p.length < LONGUEUR_MINIMALE) return Robustesse(Niveau.TropCourte, 0.0, p.length)
        val bits = bitsEstimes(p)
        val niveau = when {
            bits < BITS_ACCEPTABLE -> Niveau.Faible
            bits < BITS_SOLIDE -> Niveau.Correcte
            else -> Niveau.Solide
        }
        return Robustesse(niveau, bits, p.length)
    }

    /**
     * Estimation volontairement prudente de l'entropie d'une phrase humaine : on retire d'abord
     * les répétitions et les suites connues, puis on garde le meilleur de deux lectures (par mots,
     * par caractères) qui sous-estiment toutes deux ce qu'un hasard pur donnerait.
     */
    internal fun bitsEstimes(normalisee: String): Double {
        var reste = normalisee
        MOTIFS_CONNUS.forEach { reste = reste.replace(it, "") }
        reste = reste.replace(Regex("(.{2,}?)\\1+"), "$1") // « lapinlapinlapin » compte comme « lapin »

        val mots = reste.split(' ').filter { it.isNotEmpty() }
        val motsDistincts = mots.distinct()
        // Mots choisis par un humain : environ 11 bits pour un mot courant (2 000 mots usuels),
        // beaucoup moins pour un mot très court.
        val parMots = if (motsDistincts.size >= 2) motsDistincts.sumOf { if (it.length >= 4) 11.0 else 4.0 } else 0.0

        val sansEspaces = reste.replace(" ", "")
        val alphabet = tailleAlphabet(sansEspaces)
        val diversite = sansEspaces.toSet().size
        // Un texte écrit par un humain a bien moins d'entropie que des caractères tirés au hasard.
        val parCaracteres = if (diversite < 8) 0.0 else sansEspaces.length * log2(alphabet.toDouble()) * 0.4

        return max(parMots, parCaracteres)
    }

    private fun tailleAlphabet(s: String): Int {
        var taille = 0
        if (s.any { it in 'a'..'z' }) taille += 26
        if (s.any { it.isDigit() }) taille += 10
        if (s.any { it.isLetter() && it !in 'a'..'z' }) taille += 20 // lettres accentuées
        if (s.any { !it.isLetterOrDigit() }) taille += 30
        return max(taille, 2)
    }

    private fun log2(x: Double) = ln(x) / ln(2.0)

    /** Suites que tout dictionnaire d'attaque essaie en premier. */
    private val MOTIFS_CONNUS = listOf(
        "azerty", "qwerty", "123456789", "12345", "abcdef", "motdepasse", "mot de passe",
        "password", "jetaime", "je t'aime", "cocooning", "0000", "1111",
    )

    enum class Niveau { TropCourte, Faible, Correcte, Solide }

    data class Robustesse(val niveau: Niveau, val bitsEstimes: Double, val longueur: Int) {
        /** Vrai si la phrase peut protéger une sauvegarde. */
        val acceptable: Boolean get() = niveau == Niveau.Correcte || niveau == Niveau.Solide
    }
}
