package fr.cocooning.feature.accueil

import fr.cocooning.core.cycle.donnees.CycleRepositoryLocal
import fr.cocooning.core.cycle.donnees.SourceCyclesMemoire
import fr.cocooning.designsystem.components.PhaseCycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    private fun suivi(d: DonneesAccueil) = d.cycle as ResumeCycle.Suivi

    @Test
    fun `l'exemple passe par les vrais calculs`() = runTest {
        val cycle = suivi(AccueilRepositoryFictif(horloge, latenceMs = 0).donnees().first())
        assertEquals(PhaseCycle.Luteale, cycle.phase)
        assertEquals(22, cycle.jour)
        assertFalse(cycle.reglesEnCours)
    }

    @Test
    fun `basculer les regles demarre le compteur puis le termine`() = runTest {
        val depot = AccueilRepositoryFictif(horloge, latenceMs = 0)
        val vm = AccueilViewModel(depot)
        backgroundScope.launch { vm.etat.collect {} }
        val annonces = mutableListOf<String>()

        vm.basculerRegles { annonces += it }
        advanceUntilIdle()
        val debut = suivi(depot.donnees().first())
        assertTrue(debut.reglesEnCours)
        assertEquals(1, debut.jour)
        assertEquals(PhaseCycle.Menstruelle, debut.phase)

        vm.basculerRegles { annonces += it }
        advanceUntilIdle()
        assertFalse(suivi(depot.donnees().first()).reglesEnCours)
        assertEquals(
            listOf(
                "C'est noté : début des règles aujourd'hui, jour 1 du cycle.",
                "C'est noté : fin des règles aujourd'hui, après 1 jour.",
            ),
            annonces,
        )
    }

    @Test
    fun `sans historique l'accueil invite a la premiere saisie`() = runTest {
        val cycles = CycleRepositoryLocal(SourceCyclesMemoire())
        val jours = MutableStateFlow(LocalDate.of(2026, 11, 27))
        val depot = AccueilRepositoryLocal(cycles, horloge, jours)
        assertEquals(ResumeCycle.PremiereSaisie, depot.donnees().first().cycle)

        assertEquals(ResultatBascule.Debut, depot.basculerRegles())
        // Le lendemain, le jour J avance sans nouvelle saisie.
        jours.value = LocalDate.of(2026, 11, 28)
        assertEquals(2, suivi(depot.donnees().first()).jour)
    }

    @Test
    fun `textes de la carte de phase`() {
        val plage = LocalDate.of(2026, 12, 3)..LocalDate.of(2026, 12, 6)
        val base = ResumeCycle.Suivi(PhaseCycle.Luteale, 22, emptyList(), plage, reglesEnCours = false)
        assertEquals("Prochaines règles estimées entre le 3 et le 6 décembre.", texteCycle(base))
        assertEquals(
            "Règles en cours. Pense à indiquer quand elles se terminent.",
            texteCycle(base.copy(phase = PhaseCycle.Menstruelle, reglesEnCours = true)),
        )
        assertEquals(
            "Fin des règles non indiquée, estimée à 5 jours. Prochaines règles estimées entre le 3 et le 6 décembre.",
            texteCycle(base.copy(finOubliee = true)),
        )
        assertEquals(
            "Règles attendues entre le 3 et le 6 décembre, pas encore indiquées.",
            texteCycle(base.copy(joursDeRetard = 2)),
        )
        assertEquals("C'est noté : fin des règles aujourd'hui, après 4 jours.", annonce(ResultatBascule.Fin(4)))
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
