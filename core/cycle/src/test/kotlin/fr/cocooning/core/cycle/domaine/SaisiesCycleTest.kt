package fr.cocooning.core.cycle.domaine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SaisiesCycleTest {

    private val d0: LocalDate = LocalDate.of(2026, 3, 1)
    private val p = ParametresCycle()

    @Test
    fun `sans historique le bouton commence un cycle`() {
        assertEquals(OperationCycle.Commencer(d0), SaisiesCycle.basculer(emptyList(), p, d0))
    }

    @Test
    fun `pendant les regles le bouton les termine`() {
        val h = listOf(CycleSaisi(7, d0))
        assertEquals(OperationCycle.Terminer(7, d0.plusDays(3)), SaisiesCycle.basculer(h, p, d0.plusDays(3)))
        // Même le jour du début : des règles d'un jour.
        assertEquals(OperationCycle.Terminer(7, d0), SaisiesCycle.basculer(h, p, d0))
    }

    @Test
    fun `un appui en trop le jour de la fin annule la fin`() {
        val h = listOf(CycleSaisi(7, d0, d0.plusDays(4)))
        assertEquals(OperationCycle.Rouvrir(7), SaisiesCycle.basculer(h, p, d0.plusDays(4)))
    }

    @Test
    fun `apres la fin le bouton commence le cycle suivant`() {
        val h = listOf(CycleSaisi(7, d0, d0.plusDays(4)))
        assertEquals(OperationCycle.Commencer(d0.plusDays(27)), SaisiesCycle.basculer(h, p, d0.plusDays(27)))
    }

    @Test
    fun `fin oubliee le bouton commence le cycle suivant`() {
        val h = listOf(CycleSaisi(7, d0))
        val jour29 = d0.plusDays(28)
        assertEquals(OperationCycle.Commencer(jour29), SaisiesCycle.basculer(h, p, jour29))
        // Mais au 10e jour, les règles sont encore considérées en cours.
        val jour10 = d0.plusDays(9)
        assertEquals(OperationCycle.Terminer(7, jour10), SaisiesCycle.basculer(h, p, jour10))
    }

    private val historique = listOf(
        CycleSaisi(1, d0, d0.plusDays(4)),
        CycleSaisi(2, d0.plusDays(28), d0.plusDays(32)),
        CycleSaisi(3, d0.plusDays(57), null),
    )
    private val aujourdhui = d0.plusDays(60)

    @Test
    fun `corrections acceptees`() {
        assertNull(SaisiesCycle.validerDebut(historique, 2, d0.plusDays(26), aujourdhui))
        assertNull(SaisiesCycle.validerFin(historique, 2, d0.plusDays(30), aujourdhui))
        assertNull(SaisiesCycle.validerFin(historique, 3, aujourdhui, aujourdhui))
        assertNull(SaisiesCycle.validerFin(historique, 2, null, aujourdhui))
    }

    @Test
    fun `corrections refusees`() {
        assertEquals(ErreurSaisie.DateFuture, SaisiesCycle.validerFin(historique, 3, aujourdhui.plusDays(1), aujourdhui))
        assertEquals(ErreurSaisie.DateFuture, SaisiesCycle.validerNouveauDebut(historique, aujourdhui.plusDays(1), aujourdhui))
        assertEquals(ErreurSaisie.FinAvantDebut, SaisiesCycle.validerFin(historique, 2, d0.plusDays(27), aujourdhui))
        assertEquals(ErreurSaisie.DebutDejaPris, SaisiesCycle.validerDebut(historique, 2, d0, aujourdhui))
        // Le 2e cycle ne peut pas commencer pendant les règles du 1er (jusqu'au 5 mars).
        assertEquals(ErreurSaisie.ChevaucheCyclePrecedent, SaisiesCycle.validerDebut(historique, 2, d0.plusDays(4), aujourdhui))
        // Les règles du 2e cycle ne peuvent pas déborder sur le 3e.
        assertEquals(ErreurSaisie.ChevaucheCycleSuivant, SaisiesCycle.validerFin(historique, 2, d0.plusDays(57), aujourdhui))
        assertEquals(ErreurSaisie.CycleInconnu, SaisiesCycle.validerFin(historique, 99, d0, aujourdhui))
    }

    @Test
    fun `saisir un cycle oublie entre deux cycles`() {
        val h = listOf(CycleSaisi(1, d0, d0.plusDays(4)), CycleSaisi(2, d0.plusDays(56), null))
        assertNull(SaisiesCycle.validerNouveauDebut(h, d0.plusDays(28), d0.plusDays(60)))
    }
}
