package fr.cocooning.core.securite.sauvegarde

import fr.cocooning.core.securite.sauvegarde.PhraseRecuperation.Niveau
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SauvegardeChiffreeTest {

    /** Coût réduit : les tests vérifient le format, pas la lenteur. */
    private val rapide = ParametresArgon2(memoireKio = 1024, iterations = 1, parallelisme = 1)
    private val phrase = "Le chat de Mymy dort sur le radiateur bleu"
    private val contenu = ContenuSauvegarde(
        mapOf("cle/cocooning_cycle.db" to "0123abcd".toByteArray(), "base/cocooning_cycle.db" to ByteArray(5000) { it.toByte() }),
    )

    @Test
    fun unFichierScelleEstReconnuEtUnAutreNon() {
        assertTrue(SauvegardeChiffree.estUneSauvegarde(SauvegardeChiffree.sceller(contenu, phrase, rapide)))
        assertFalse(SauvegardeChiffree.estUneSauvegarde("PK pas une sauvegarde".toByteArray()))
        assertFalse(SauvegardeChiffree.estUneSauvegarde(ByteArray(3)))
    }

    @Test
    fun allerRetourAvecLaBonnePhrase() {
        val fichier = SauvegardeChiffree.sceller(contenu, phrase, rapide)
        val relu = SauvegardeChiffree.ouvrir(fichier, phrase)
        assertEquals(contenu.entrees.keys, relu.entrees.keys)
        contenu.entrees.forEach { (nom, octets) -> assertArrayEquals(octets, relu[nom]) }
    }

    @Test
    fun majusculesEspacesEtAccentsComposesNeComptentPas() {
        val fichier = SauvegardeChiffree.sceller(contenu, "Mon été à Biarritz avec Mymy", rapide)
        // « é » décomposé (e + accent), majuscules différentes, espaces en trop.
        val relu = SauvegardeChiffree.ouvrir(fichier, "  mon été   à BIARRITZ avec mymy ")
        assertArrayEquals(contenu["cle/cocooning_cycle.db"], relu["cle/cocooning_cycle.db"])
    }

    @Test(expected = EchecOuverture.PhraseIncorrecte::class)
    fun uneMauvaisePhraseEstRefusee() {
        val fichier = SauvegardeChiffree.sceller(contenu, phrase, rapide)
        SauvegardeChiffree.ouvrir(fichier, "Le chat de Mymy dort sur le radiateur vert")
    }

    @Test(expected = EchecOuverture.PhraseIncorrecte::class)
    fun baisserLeCoutDansLEnteteRendLeFichierIllisible() {
        val fichier = SauvegardeChiffree.sceller(contenu, phrase, ParametresArgon2(2048, 1, 1))
        // Mémoire écrite juste après le magique (7), la version (1) et l'algorithme (1).
        fichier[9 + 2] = 4 // 2048 Kio -> 1024 Kio
        SauvegardeChiffree.ouvrir(fichier, phrase)
    }

    @Test(expected = EchecOuverture.FichierIllisible::class)
    fun unCoutDemesureEstRefuseAvantToutCalcul() {
        val fichier = SauvegardeChiffree.sceller(contenu, phrase, rapide)
        fichier[9] = 0x7f // environ 2 To de mémoire demandés
        SauvegardeChiffree.ouvrir(fichier, phrase)
    }

    @Test(expected = EchecOuverture.FichierIllisible::class)
    fun unAutreFichierEstRefuse() {
        SauvegardeChiffree.ouvrir("PK\u0003\u0004 pas une sauvegarde".toByteArray(), phrase)
    }

    @Test
    fun leContenuNApparaitPasEnClair() {
        val marque = "MARQUE-SECRETE-123".toByteArray()
        val fichier = SauvegardeChiffree.sceller(ContenuSauvegarde(mapOf("x" to marque)), phrase, rapide)
        assertFalse(String(fichier, Charsets.ISO_8859_1).contains("MARQUE"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun uneSauvegardeExigeUnePhraseAcceptable() {
        SauvegardeChiffree.sceller(contenu, "azertyazertyazerty123", rapide)
    }

    @Test
    fun lesParametresParDefautSuiventLaRfc9106() {
        val p = ParametresArgon2.PAR_DEFAUT
        assertEquals(65536, p.memoireKio)
        assertEquals(3, p.iterations)
    }

    @Test
    fun laDerivationDependDeLaPhraseDuSelEtDuCout() {
        val sel = ByteArray(16) { 2 }
        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
        val reference = hex(SauvegardeChiffree.deriver(phrase, sel, rapide))
        assertEquals(64, reference.length)
        assertEquals(reference, hex(SauvegardeChiffree.deriver(phrase.uppercase(), sel, rapide)))
        assertTrue(reference != hex(SauvegardeChiffree.deriver(phrase + " !", sel, rapide)))
        assertTrue(reference != hex(SauvegardeChiffree.deriver(phrase, ByteArray(16) { 3 }, rapide)))
        assertTrue(reference != hex(SauvegardeChiffree.deriver(phrase, sel, rapide.copy(iterations = 2))))
    }
}

class PhraseRecuperationTest {

    private fun niveau(p: String) = PhraseRecuperation.evaluer(p).niveau

    @Test
    fun tropCourte() {
        assertEquals(Niveau.TropCourte, niveau("MonChat2024!"))
        assertEquals(Niveau.TropCourte, niveau("   court     mais espacé  ")) // les espaces en trop ne comptent pas
    }

    @Test
    fun lesPhrasesFaiblesSontRefusees() {
        assertEquals(Niveau.Faible, niveau("aaaaaaaaaaaaaaaaaaaaaaaaa"))
        assertEquals(Niveau.Faible, niveau("lapinbleulapinbleulapinbleu"))
        assertEquals(Niveau.Faible, niveau("azertyuiop1234567890"))
        assertEquals(Niveau.Faible, niveau("motdepasse motdepasse motdepasse"))
        assertFalse(PhraseRecuperation.evaluer("cocooning cocooning 123456789").acceptable)
    }

    @Test
    fun unePhraseDeQuelquesMotsEstAcceptee() {
        val correcte = PhraseRecuperation.evaluer("Mymy aime les crêpes au sucre")
        assertTrue(correcte.acceptable)
        assertEquals(Niveau.Solide, niveau("Le chat de Mymy dort sur le radiateur bleu"))
    }
}
