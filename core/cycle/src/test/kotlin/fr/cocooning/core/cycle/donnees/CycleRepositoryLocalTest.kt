package fr.cocooning.core.cycle.donnees

import fr.cocooning.core.cycle.domaine.CalculateurCycle
import fr.cocooning.core.cycle.domaine.CycleSaisi
import fr.cocooning.core.cycle.domaine.ErreurSaisie
import fr.cocooning.core.cycle.domaine.EtatCycle
import fr.cocooning.core.cycle.domaine.OperationCycle
import fr.cocooning.core.cycle.domaine.ParametresCycle
import fr.cocooning.core.cycle.domaine.Phase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class CycleRepositoryLocalTest {

    private val d0: LocalDate = LocalDate.of(2026, 11, 2)
    private val source = SourceCyclesMemoire()
    private val depot = CycleRepositoryLocal(source)

    private suspend fun etat(jour: LocalDate) =
        CalculateurCycle.etat(depot.historique().first(), depot.parametres().first(), jour) as EtatCycle.Suivi

    @Test
    fun `un cycle complet saisi au bouton`() = runTest {
        assertEquals(ParametresCycle(), depot.parametres().first())

        val debut = depot.basculerRegles(d0) as ResultatSaisie.Enregistree
        assertTrue(debut.operation is OperationCycle.Commencer)
        assertEquals(1, etat(d0).jour)

        // Règles finies au 4e jour.
        val fin = depot.basculerRegles(d0.plusDays(3)) as ResultatSaisie.Enregistree
        assertTrue(fin.operation is OperationCycle.Terminer)
        assertEquals(Phase.Folliculaire, etat(d0.plusDays(4)).phase)
        assertEquals(4, etat(d0.plusDays(4)).dureeRegles)

        // Cycle suivant 30 jours plus tard : la moyenne passe à (30 + 28) / 2.
        depot.basculerRegles(d0.plusDays(30))
        assertEquals(2, depot.historique().first().size)
        assertEquals(29, etat(d0.plusDays(30)).dureeCycle.jours)
    }

    @Test
    fun `un appui en trop est annule par un second appui`() = runTest {
        depot.basculerRegles(d0)
        depot.basculerRegles(d0.plusDays(2))
        val reprise = depot.basculerRegles(d0.plusDays(2)) as ResultatSaisie.Enregistree
        assertTrue(reprise.operation is OperationCycle.Rouvrir)
        assertTrue(etat(d0.plusDays(2)).reglesEnCours)
    }

    @Test
    fun `corriger une date de debut`() = runTest {
        depot.basculerRegles(d0)
        val id = depot.historique().first().single().id
        assertEquals(ResultatSaisie.Enregistree(null), depot.corrigerDebut(id, d0.minusDays(1), d0))
        assertEquals(2, etat(d0).jour)
        assertEquals(
            ResultatSaisie.Refusee(ErreurSaisie.DateFuture),
            depot.corrigerDebut(id, d0.plusDays(1), d0),
        )
    }

    @Test
    fun `corriger puis effacer une fin`() = runTest {
        depot.basculerRegles(d0)
        depot.basculerRegles(d0.plusDays(5))
        val id = depot.historique().first().single().id
        depot.corrigerFin(id, d0.plusDays(3), d0.plusDays(6))
        assertEquals(4, etat(d0.plusDays(6)).dureeRegles)
        depot.corrigerFin(id, null, d0.plusDays(6))
        assertTrue(etat(d0.plusDays(6)).reglesEnCours)
    }

    @Test
    fun `supprimer un cycle saisi par erreur`() = runTest {
        depot.basculerRegles(d0)
        val id = depot.historique().first().single().id
        assertEquals(ResultatSaisie.Enregistree(null), depot.supprimer(id))
        assertEquals(ResultatSaisie.Refusee(ErreurSaisie.CycleInconnu), depot.supprimer(id))
    }

    @Test
    fun `les durees declarees sont utilisees`() = runTest {
        depot.enregistrerParametres(ParametresCycle(dureeCycle = 32, dureeRegles = 6))
        depot.basculerRegles(d0)
        assertEquals(32, etat(d0).dureeCycle.jours)
    }

    @Test
    fun `une panne du stockage devient un echec sans exception`() = runTest {
        val enPanne = SourceCyclesMemoire(listOf(CycleSaisi(1, d0))).apply { panne = IOException("disque plein") }
        val resultat = CycleRepositoryLocal(enPanne).basculerRegles(d0.plusDays(2))
        assertTrue(resultat is ResultatSaisie.Echec)
    }
}
