package fr.cocooning.core.securite

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.SecureRandom

class BaseChiffreeTest {

    @get:Rule
    val dossier = TemporaryFolder()

    @Test
    fun `une base absente ou vide est absente`() {
        val base = File(dossier.root, "absente.db")
        assertEquals(EtatFichier.Absent, FichierBase.etat(base))
        base.writeBytes(ByteArray(0))
        assertEquals(EtatFichier.Absent, FichierBase.etat(base))
    }

    @Test
    fun `l'entête SQLite signale une base en clair`() {
        val base = File(dossier.root, "claire.db")
        base.writeBytes("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(100))
        assertEquals(EtatFichier.EnClair, FichierBase.etat(base))
    }

    @Test
    fun `des octets quelconques signalent une base chiffrée`() {
        val base = File(dossier.root, "chiffree.db")
        base.writeBytes(ByteArray(4096).also(SecureRandom()::nextBytes).also { it[0] = 0x42 })
        assertEquals(EtatFichier.Chiffre, FichierBase.etat(base))
    }

    @Test
    fun `une base plus courte que l'entête n'est pas en clair`() {
        assertFalse(FichierBase.estEnClair("SQLite".toByteArray()))
    }

    @Test
    fun `mettre de côté renomme la base et ses compagnons`() {
        val base = File(dossier.root, "cycle.db").apply { writeText("x") }
        File(base.path + "-wal").writeText("w")
        val ecartee = FichierBase.mettreDeCote(base, 1234L)
        assertFalse(base.exists())
        assertFalse(File(base.path + "-wal").exists())
        assertEquals("cycle.db.illisible-1234", ecartee.name)
        assertTrue(ecartee.exists())
        assertTrue(File(ecartee.path + "-wal").exists())
    }

    @Test
    fun `la phrase de passe fait 64 caractères hexadécimaux et change à chaque tirage`() {
        val a = PhraseDePasse.nouvelle(SecureRandom())
        val b = PhraseDePasse.nouvelle(SecureRandom())
        assertEquals(64, a.size)
        assertTrue(PhraseDePasse.estHexadecimale(a))
        assertFalse(a.contentEquals(b))
        assertFalse(PhraseDePasse.estHexadecimale("abc'; DROP".toByteArray()))
    }

    @Test
    fun `l'enveloppe se relit à l'identique`() {
        val enveloppe = Enveloppe(iv = ByteArray(12) { it.toByte() }, contenu = ByteArray(80) { (it * 3).toByte() })
        val relue = Enveloppe.depuisOctets(enveloppe.versOctets())
        assertArrayEquals(enveloppe.iv, relue.iv)
        assertArrayEquals(enveloppe.contenu, relue.contenu)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `une enveloppe d'une autre version est refusée`() {
        Enveloppe.depuisOctets(byteArrayOf(9, 12) + ByteArray(40))
    }
}
