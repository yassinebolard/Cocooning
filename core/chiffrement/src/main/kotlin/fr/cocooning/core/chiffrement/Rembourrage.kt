package fr.cocooning.core.chiffrement

/**
 * Complète un message avant chiffrement pour que sa taille chiffrée ne trahisse pas la longueur
 * exacte du texte (ADR 0003) : un octet 0x80, puis des zéros jusqu'au multiple de 160 octets
 * suivant, comme Signal.
 */
object Rembourrage {
    const val BLOC = 160
    private const val MARQUE: Byte = 0x80.toByte()

    fun ajouter(clair: ByteArray): ByteArray {
        val taille = (clair.size / BLOC + 1) * BLOC
        return clair.copyOf(taille).also { it[clair.size] = MARQUE }
    }

    /** Retire le rembourrage ; refuse un contenu qui n'en a pas. */
    fun retirer(rembourre: ByteArray): ByteArray {
        var fin = rembourre.size - 1
        while (fin >= 0 && rembourre[fin] == 0.toByte()) fin--
        if (fin < 0 || rembourre[fin] != MARQUE) {
            throw ExceptionChiffrement(ExceptionChiffrement.Raison.MessageInvalide)
        }
        return rembourre.copyOf(fin)
    }
}
