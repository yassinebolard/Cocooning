package fr.cocooning.feature.sauvegarde

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.cocooning.core.securite.sauvegarde.ContenuSauvegarde
import fr.cocooning.core.securite.sauvegarde.EchecOuverture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val ENTETE = "COCOSAV".toByteArray(Charsets.US_ASCII)
private const val BONNE_PHRASE = "parapluie violet mange trois cerises"

private class CoffreFactice : CoffreSauvegarde {
    var exports = 0
    var restaure: ContenuSauvegarde? = null
    var echecRestauration = false
    var illisible: String? = null

    override suspend fun exporter(phrase: String): ByteArray {
        exports++
        return ENTETE + phrase.toByteArray()
    }

    override suspend fun ouvrir(fichier: ByteArray, phrase: String): ContenuSauvegarde {
        illisible?.let { throw EchecOuverture.FichierIllisible(it) }
        if (phrase.trim().lowercase() != BONNE_PHRASE) throw EchecOuverture.PhraseIncorrecte()
        return ContenuSauvegarde(mapOf("base/x" to byteArrayOf(1, 2, 3)))
    }

    override suspend fun restaurer(contenu: ContenuSauvegarde) {
        if (echecRestauration) error("disque plein")
        restaure = contenu
    }
}

private class FichiersFactices : FichiersSauvegarde {
    val ecrits = mutableMapOf<String, ByteArray>()
    val disponibles = mutableMapOf<String, ByteArray>()
    var echecEcriture = false

    override suspend fun ecrire(adresse: String, octets: ByteArray) {
        if (echecEcriture) throw IOException("plein")
        ecrits[adresse] = octets
    }

    override suspend fun lire(adresse: String): ByteArray = disponibles[adresse] ?: throw IOException("absent")
    override suspend fun nom(adresse: String): String = adresse.substringAfterLast('/')
}

private class SuiviFactice(dernier: LocalDate? = null) : SuiviSauvegarde {
    val etat = MutableStateFlow(Triple(dernier, null as LocalDate?, false))
    override val dernierExport = etat.map { it.first }
    override val rappelReporteJusquau = etat.map { it.second }
    override val premierLancementTermine = etat.map { it.third }
    override suspend fun noterExport(jour: LocalDate) = etat.update3 { Triple(jour, null, it.third) }
    override suspend fun reporterRappel(jusquau: LocalDate) = etat.update3 { Triple(it.first, jusquau, it.third) }
    override suspend fun terminerPremierLancement() = etat.update3 { Triple(it.first, it.second, true) }

    private fun <T> MutableStateFlow<T>.update3(f: (T) -> T) { value = f(value) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SauvegardeViewModelsTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private val horloge = Clock.fixed(Instant.parse("2026-11-27T08:00:00Z"), ZoneId.of("Europe/Paris"))
    private val aujourdhui = LocalDate.of(2026, 11, 27)

    private val coffre = CoffreFactice()
    private val fichiers = FichiersFactices()

    private fun dependances(suivi: SuiviSauvegarde = SuiviFactice()) =
        DependancesSauvegarde(coffre, fichiers, suivi, horloge)

    @Before
    fun preparer() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun nettoyer() = Dispatchers.resetMain()

    // Rappel

    @Test
    fun `le rappel est du sans sauvegarde, puis tous les trente jours`() {
        assertTrue(RappelExport.estDu(null, null, aujourdhui))
        assertFalse(RappelExport.estDu(aujourdhui.minusDays(29), null, aujourdhui))
        assertTrue(RappelExport.estDu(aujourdhui.minusDays(30), null, aujourdhui))
    }

    @Test
    fun `plus tard cache le rappel jusqu'a la date de report`() {
        assertFalse(RappelExport.estDu(null, aujourdhui.plusDays(1), aujourdhui))
        assertTrue(RappelExport.estDu(null, aujourdhui, aujourdhui))
    }

    @Test
    fun `le rappel de l'accueil disparait apres plus tard et revient une semaine apres`() = runTest {
        val suivi = SuiviFactice()
        val vm = RappelViewModel(dependances(suivi))
        backgroundScope.launch { vm.etat.collect {} }
        advanceUntilIdle()
        assertTrue(vm.etat.value.visible)
        vm.reporter()
        advanceUntilIdle()
        assertFalse(vm.etat.value.visible)
        assertEquals(aujourdhui.plusDays(RappelExport.REPORT_JOURS), suivi.rappelReporteJusquau.first())
    }

    // Export

    @Test
    fun `l'export refuse d'ouvrir le selecteur tant qu'il manque quelque chose`() = runTest {
        val vm = ExportViewModel(dependances())
        vm.changerPhrase("chat chien")
        assertFalse(vm.pretPourChoisirFichier())
        assertEquals(3, vm.formulaire.value.manques.size)

        vm.changerPhrase(BONNE_PHRASE)
        vm.changerConfirmation("Parapluie  violet mange trois cerises ")
        assertFalse(vm.pretPourChoisirFichier())
        assertEquals(1, vm.formulaire.value.manques.size)

        vm.indiquerCompris(true)
        assertTrue(vm.pretPourChoisirFichier())
        assertTrue(vm.formulaire.value.manques.isEmpty())
    }

    @Test
    fun `la confirmation differente n'est signalee qu'une fois assez longue`() {
        val vm = ExportViewModel(dependances())
        vm.changerPhrase(BONNE_PHRASE)
        vm.changerConfirmation("parapluie")
        assertNull(vm.formulaire.value.erreurConfirmation)
        vm.changerConfirmation("parapluie violet mange trois prunes")
        assertNotNull(vm.formulaire.value.erreurConfirmation)
    }

    @Test
    fun `l'export ecrit le fichier, note la date et oublie la phrase`() = runTest {
        val suivi = SuiviFactice()
        val vm = ExportViewModel(dependances(suivi))
        vm.changerPhrase(BONNE_PHRASE)
        vm.changerConfirmation(BONNE_PHRASE)
        vm.indiquerCompris(true)
        vm.exporterVers("content://docs/cocooning.cocooning")
        advanceUntilIdle()

        val f = vm.formulaire.value
        assertEquals(EtapeExport.Termine, f.etape)
        assertEquals("", f.phrase)
        assertEquals("", f.confirmation)
        assertNotNull(fichiers.ecrits["content://docs/cocooning.cocooning"])
        assertEquals(aujourdhui, suivi.dernierExport.first())
    }

    @Test
    fun `annuler le selecteur ne fait rien`() = runTest {
        val vm = ExportViewModel(dependances())
        vm.changerPhrase(BONNE_PHRASE)
        vm.changerConfirmation(BONNE_PHRASE)
        vm.indiquerCompris(true)
        vm.exporterVers(null)
        advanceUntilIdle()
        assertEquals(EtapeExport.Saisie, vm.formulaire.value.etape)
        assertEquals(0, coffre.exports)
    }

    @Test
    fun `un echec d'ecriture garde la phrase et dit quoi faire`() = runTest {
        fichiers.echecEcriture = true
        val suivi = SuiviFactice()
        val vm = ExportViewModel(dependances(suivi))
        vm.changerPhrase(BONNE_PHRASE)
        vm.changerConfirmation(BONNE_PHRASE)
        vm.indiquerCompris(true)
        vm.exporterVers("content://docs/x")
        advanceUntilIdle()
        val f = vm.formulaire.value
        assertEquals(EtapeExport.Saisie, f.etape)
        assertEquals(BONNE_PHRASE, f.phrase)
        assertNotNull(f.erreur)
        assertNull(suivi.dernierExport.first())
    }

    @Test
    fun `le nom propose porte la date et l'extension`() {
        assertEquals("cocooning-sauvegarde-2026-11-27.cocooning", ExportViewModel.nomFichier(aujourdhui))
    }

    // Restauration

    private fun restaurationAvecFichier(suivi: SuiviSauvegarde = SuiviFactice()): RestaurationViewModel {
        fichiers.disponibles["content://docs/sauvegarde.cocooning"] = ENTETE + byteArrayOf(9, 9)
        val vm = RestaurationViewModel(dependances(suivi))
        vm.fichierChoisi("content://docs/sauvegarde.cocooning")
        return vm
    }

    @Test
    fun `un fichier qui n'est pas une sauvegarde est refuse tout de suite`() = runTest {
        fichiers.disponibles["content://docs/photo.jpg"] = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val vm = RestaurationViewModel(dependances())
        vm.fichierChoisi("content://docs/photo.jpg")
        advanceUntilIdle()
        val f = vm.formulaire.value
        assertEquals(EtapeRestauration.ChoixFichier, f.etape)
        assertEquals(RestaurationViewModel.PAS_UNE_SAUVEGARDE, f.erreurFichier)
    }

    @Test
    fun `une mauvaise phrase est signalee sur le champ et ne restaure rien`() = runTest {
        val vm = restaurationAvecFichier()
        advanceUntilIdle()
        assertEquals(EtapeRestauration.Saisie, vm.formulaire.value.etape)
        assertEquals("sauvegarde.cocooning", vm.formulaire.value.nomFichier)

        vm.changerPhrase("une phrase qui n'est pas la bonne du tout")
        vm.restaurer()
        advanceUntilIdle()
        val f = vm.formulaire.value
        assertEquals(EtapeRestauration.Saisie, f.etape)
        assertEquals(RestaurationViewModel.PHRASE_INCORRECTE, f.erreurPhrase)
        assertNull(coffre.restaure)
    }

    @Test
    fun `une phrase vide est demandee sans lancer la verification`() = runTest {
        val vm = restaurationAvecFichier()
        advanceUntilIdle()
        vm.restaurer()
        assertNotNull(vm.formulaire.value.erreurPhrase)
        assertEquals(EtapeRestauration.Saisie, vm.formulaire.value.etape)
    }

    @Test
    fun `la bonne phrase restaure et termine le premier lancement`() = runTest {
        val suivi = SuiviFactice()
        val vm = restaurationAvecFichier(suivi)
        advanceUntilIdle()
        vm.changerPhrase("  Parapluie violet mange trois cerises")
        vm.restaurer()
        advanceUntilIdle()
        assertEquals(EtapeRestauration.Termine, vm.formulaire.value.etape)
        assertEquals("", vm.formulaire.value.phrase)
        assertNotNull(coffre.restaure)
        assertTrue(suivi.premierLancementTermine.first())
    }

    @Test
    fun `une sauvegarde plus recente demande une mise a jour`() = runTest {
        coffre.illisible = "Sauvegarde d'une version plus récente de Cocooning"
        val vm = restaurationAvecFichier()
        advanceUntilIdle()
        vm.changerPhrase(BONNE_PHRASE)
        vm.restaurer()
        advanceUntilIdle()
        val f = vm.formulaire.value
        assertEquals(EtapeRestauration.ChoixFichier, f.etape)
        assertTrue(f.erreurFichier!!.contains("Mets l'appli à jour"))
    }

    @Test
    fun `un echec de remise en place laisse reessayer`() = runTest {
        coffre.echecRestauration = true
        val suivi = SuiviFactice()
        val vm = restaurationAvecFichier(suivi)
        advanceUntilIdle()
        vm.changerPhrase(BONNE_PHRASE)
        vm.restaurer()
        advanceUntilIdle()
        val f = vm.formulaire.value
        assertEquals(EtapeRestauration.Saisie, f.etape)
        assertEquals(RestaurationViewModel.ECHEC_RESTAURATION, f.erreur)
        assertFalse(suivi.premierLancementTermine.first())
    }

    // Préférences

    @Test
    fun `le suivi en preferences retient l'export, le report et le premier lancement`() = runTest {
        val magasin = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            dossier.newFile("suivi.preferences_pb").also { it.delete() }
        }
        val suivi = SuiviSauvegardePreferences(magasin)
        assertNull(suivi.dernierExport.first())
        assertFalse(suivi.premierLancementTermine.first())

        suivi.reporterRappel(aujourdhui.plusDays(7))
        assertEquals(aujourdhui.plusDays(7), suivi.rappelReporteJusquau.first())
        suivi.noterExport(aujourdhui)
        assertEquals(aujourdhui, suivi.dernierExport.first())
        assertNull("un export efface le report", suivi.rappelReporteJusquau.first())
        suivi.terminerPremierLancement()
        assertTrue(suivi.premierLancementTermine.first())
    }

    @Test
    fun `un telephone qui a deja des donnees saute l'accueil du premier lancement`() = runTest {
        val magasin = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            dossier.newFile("existant.preferences_pb").also { it.delete() }
        }
        assertTrue(SuiviSauvegardePreferences(magasin) { true }.premierLancementTermine.first())
    }
}
