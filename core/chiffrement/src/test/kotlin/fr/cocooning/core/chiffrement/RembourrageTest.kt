package fr.cocooning.core.chiffrement

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RembourrageTest {

    @Test
    fun `aller-retour pour toutes les tailles autour d'un bloc`() {
        for (taille in listOf(0, 1, 158, 159, 160, 161, 319, 320, 5000)) {
            val clair = ByteArray(taille) { (it % 251 + 1).toByte() }
            val rembourre = Rembourrage.ajouter(clair)
            assertEquals("taille $taille", 0, rembourre.size % Rembourrage.BLOC)
            assertArrayEquals("taille $taille", clair, Rembourrage.retirer(rembourre))
        }
    }

    @Test
    fun `un contenu qui se termine par des zeros est preserve`() {
        val clair = byteArrayOf(1, 0, 0, 0)
        assertArrayEquals(clair, Rembourrage.retirer(Rembourrage.ajouter(clair)))
    }

    @Test
    fun `un contenu sans rembourrage est refuse`() {
        assertThrows(ExceptionChiffrement::class.java) { Rembourrage.retirer(ByteArray(160)) }
        assertThrows(ExceptionChiffrement::class.java) { Rembourrage.retirer(byteArrayOf(1, 2, 3)) }
    }
}
