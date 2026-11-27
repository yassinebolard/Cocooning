package fr.cocooning.feature.cycle

import fr.cocooning.core.cycle.domaine.Contraception
import fr.cocooning.core.cycle.domaine.CycleSaisi
import fr.cocooning.core.cycle.domaine.ParametresCycle
import fr.cocooning.core.cycle.donnees.CycleRepositoryLocal
import fr.cocooning.core.cycle.donnees.SourceCyclesMemoire
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CycleViewModelsTest {

    private val horloge = Clock.fixed(Instant.parse("2026-11-27T08:00:00Z"), ZoneId.of("Europe/Paris"))
    private val aujourdhui = LocalDate.of(2026, 11, 27)

    private fun dependances(source: SourceCyclesMemoire) =
        DependancesCycle(CycleRepositoryLocal(source), horloge, flowOf(aujourdhui))

    @Before
    fun preparer() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun nettoyer() = Dispatchers.resetMain()

    @Test
    fun `sans saisie initiale l'onglet la demande`() = runTest {
        val vm = CycleViewModel(dependances(SourceCyclesMemoire()))
        backgroundScope.launch { vm.etat.collect {} }
        advanceUntilIdle()
        assertEquals(CycleUiState.PremiereSaisie, vm.etat.value)
    }

    @Test
    fun `la saisie initiale cree le premier cycle et ouvre le suivi`() = runTest {
        val source = SourceCyclesMemoire()
        val deps = dependances(source)
        val cycle = CycleViewModel(deps)
        backgroundScope.launch { cycle.etat.collect {} }
        val config = ConfigurationViewModel(deps)
        advanceUntilIdle()

        val f = config.formulaire.value
        assertTrue(f.charge)
        assertTrue(f.demanderDate)
        assertFalse(f.modification)

        config.choisirDernieresRegles(aujourdhui.minusDays(12))
        config.choisirFin(aujourdhui.minusDays(8))
        config.changerDureeCycle(31)
        config.choisirContraception(Contraception.Preservatif)
        config.enregistrer()
        advanceUntilIdle()

        assertTrue(config.formulaire.value.enregistre)
        val pret = cycle.etat.value as CycleUiState.Pret
        assertEquals(Contraception.Preservatif, pret.parametres.contraception)
        assertEquals(31, pret.parametres.dureeCycle)
        val premier = pret.bilan.cycles.single()
        assertEquals(5, premier.dureeRegles)
    }

    @Test
    fun `des regles encore en cours n'ont pas de fin`() = runTest {
        val source = SourceCyclesMemoire()
        val config = ConfigurationViewModel(dependances(source))
        advanceUntilIdle()
        config.choisirDernieresRegles(aujourdhui.minusDays(2))
        config.choisirFin(aujourdhui.minusDays(1))
        config.indiquerReglesEnCours(true)
        config.enregistrer()
        advanceUntilIdle()
        assertNull(source.cycles().single().finRegles)
    }

    @Test
    fun `choisir deux fois la meme contraception la retire`() = runTest {
        val config = ConfigurationViewModel(dependances(SourceCyclesMemoire()))
        config.choisirContraception(Contraception.Pilule)
        config.choisirContraception(Contraception.Pilule)
        assertNull(config.formulaire.value.contraception)
    }

    @Test
    fun `la modification des reglages reprend les valeurs declarees sans redemander la date`() = runTest {
        val source = SourceCyclesMemoire(
            initial = listOf(CycleSaisi(1, aujourdhui.minusDays(10))),
            parametres = ParametresCycle(30, 6, Contraception.Implant),
        )
        val config = ConfigurationViewModel(dependances(source))
        advanceUntilIdle()
        val f = config.formulaire.value
        assertTrue(f.modification)
        assertFalse(f.demanderDate)
        assertEquals(30, f.dureeCycle)
        assertEquals(Contraception.Implant, f.contraception)
    }

    @Test
    fun `corriger les dates recalcule les moyennes`() = runTest {
        val source = SourceCyclesMemoire(
            initial = listOf(
                CycleSaisi(1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5)),
                CycleSaisi(2, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)),
                CycleSaisi(3, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5)),
            ),
            parametres = ParametresCycle(),
        )
        val deps = dependances(source)
        val cycle = CycleViewModel(deps)
        backgroundScope.launch { cycle.etat.collect {} }
        advanceUntilIdle()
        // 30 et 31 jours, plus 28 déclarés : 30.
        assertEquals(30, (cycle.etat.value as CycleUiState.Pret).bilan.dureeCycle.jours)

        val correction = CorrectionViewModel(deps, cycleId = 2)
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 10, 1), correction.formulaire.value.debut)
        correction.choisirDebut(LocalDate.of(2026, 9, 27))
        correction.choisirFin(LocalDate.of(2026, 9, 30))
        assertEquals(4, correction.formulaire.value.dureeRegles)
        correction.enregistrer()
        advanceUntilIdle()

        assertNotNull(correction.formulaire.value.termine)
        val bilan = (cycle.etat.value as CycleUiState.Pret).bilan
        // 26 et 35 jours, plus 28 déclarés : 30 (arrondi de 29,67).
        assertEquals(listOf(35, 26), bilan.cycles.mapNotNull { it.dureeCycle })
        assertEquals(30, bilan.dureeCycle.jours)
    }

    @Test
    fun `une correction refusee affiche ce qu'il faut corriger`() = runTest {
        val source = SourceCyclesMemoire(
            initial = listOf(
                CycleSaisi(1, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)),
                CycleSaisi(2, LocalDate.of(2026, 11, 1)),
            ),
        )
        val correction = CorrectionViewModel(dependances(source), cycleId = 2)
        advanceUntilIdle()
        correction.choisirDebut(LocalDate.of(2026, 10, 4))
        correction.enregistrer()
        advanceUntilIdle()
        val f = correction.formulaire.value
        assertNull(f.termine)
        assertEquals(message(fr.cocooning.core.cycle.domaine.ErreurSaisie.ChevaucheCyclePrecedent), f.erreur)
        assertEquals(LocalDate.of(2026, 11, 1), source.cycles().last().debut)
    }

    @Test
    fun `ajouter puis supprimer des regles`() = runTest {
        val source = SourceCyclesMemoire()
        val deps = dependances(source)
        val ajout = CorrectionViewModel(deps, cycleId = null)
        advanceUntilIdle()
        assertTrue(ajout.formulaire.value.ajout)
        ajout.choisirDebut(LocalDate.of(2026, 10, 20))
        ajout.enregistrer()
        advanceUntilIdle()
        val id = source.cycles().single().id

        val suppression = CorrectionViewModel(deps, cycleId = id)
        advanceUntilIdle()
        suppression.supprimer()
        advanceUntilIdle()
        assertNotNull(suppression.formulaire.value.termine)
        assertTrue(deps.depot.historique().first().isEmpty())
    }

    @Test
    fun `un cycle supprime entre-temps est signale`() = runTest {
        val correction = CorrectionViewModel(dependances(SourceCyclesMemoire()), cycleId = 42)
        advanceUntilIdle()
        assertTrue(correction.formulaire.value.introuvable)
    }
}
