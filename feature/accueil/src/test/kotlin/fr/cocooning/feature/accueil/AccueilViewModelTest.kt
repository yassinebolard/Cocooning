package fr.cocooning.feature.accueil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AccueilViewModelTest {

    private val horloge = Clock.fixed(Instant.parse("2026-11-27T08:00:00Z"), ZoneId.of("Europe/Paris"))

    @Before
    fun preparer() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun nettoyer() = Dispatchers.resetMain()

    @Test
    fun `commence en chargement puis affiche les donnees`() = runTest {
        val vm = AccueilViewModel(AccueilRepositoryFictif(horloge, latenceMs = 0))
        assertEquals(AccueilUiState.Chargement, vm.etat.value)
        backgroundScope.launch { vm.etat.collect {} }
        advanceUntilIdle()
        val pret = vm.etat.value as AccueilUiState.Pret
        assertEquals("Mymy", pret.donnees.prenom)
        assertEquals(LocalDate.of(2026, 11, 27), pret.donnees.aujourdhui)
    }

    @Test
    fun `basculer les regles entre en phase menstruelle puis en sort`() = runTest {
        val depot = AccueilRepositoryFictif(horloge, latenceMs = 0)
        val vm = AccueilViewModel(depot)
        backgroundScope.launch { vm.etat.collect {} }

        vm.basculerRegles()
        advanceUntilIdle()
        assertTrue(depot.donnees().first().cycle!!.reglesEnCours)

        vm.basculerRegles()
        advanceUntilIdle()
        assertFalse(depot.donnees().first().cycle!!.reglesEnCours)
    }

    @Test
    fun `dates et heures en francais`() {
        assertEquals("Vendredi 27 novembre", jourComplet(LocalDate.of(2026, 11, 27)))
        assertEquals("8 h 12", heure(LocalTime.of(8, 12)))
        assertEquals("19 h", heure(LocalTime.of(19, 0)))
        assertEquals("entre le 3 et le 6 décembre", fourchette(LocalDate.of(2026, 12, 3)..LocalDate.of(2026, 12, 6)))
        assertEquals(
            "entre le 30 novembre et le 2 décembre",
            fourchette(LocalDate.of(2026, 11, 30)..LocalDate.of(2026, 12, 2)),
        )
    }
}
