package fr.cocooning.core.cycle.donnees

import fr.cocooning.core.cycle.domaine.CalculateurCycle
import fr.cocooning.core.cycle.domaine.ConfigurationInitiale
import fr.cocooning.core.cycle.domaine.Contraception
import fr.cocooning.core.cycle.domaine.CycleSaisi
import fr.cocooning.core.cycle.domaine.ErreurSaisie
import fr.cocooning.core.cycle.domaine.EtatCycle
import fr.cocooning.core.cycle.domaine.OperationCycle
import fr.cocooning.core.cycle.domaine.ParametresCycle
import fr.cocooning.core.cycle.domaine.Phase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `la saisie initiale enregistre les durees, la contraception et les dernieres regles`() = runTest {
        assertNull(depot.parametresDeclares().first())
        val parametres = ParametresCycle(dureeCycle = 31, dureeRegles = 4, contraception = Contraception.SteriletCuivre)
        val resultat = depot.configurer(ConfigurationInitiale(d0, d0.plusDays(3), parametres), d0.plusDays(10))
        assertEquals(ResultatSaisie.Enregistree(null), resultat)
        assertEquals(parametres, depot.parametresDeclares().first())
        val cycle = depot.historique().first().single()
        assertEquals(d0, cycle.debut)
        assertEquals(d0.plusDays(3), cycle.finRegles)
        assertEquals(31, etat(d0.plusDays(10)).dureeCycle.jours)
    }

    @Test
    fun `la saisie initiale sans date garde l'historique tel quel`() = runTest {
        depot.basculerRegles(d0)
        depot.configurer(ConfigurationInitiale(null, null, ParametresCycle(30, 5)), d0.plusDays(1))
        assertEquals(1, depot.historique().first().size)
        assertEquals(30, depot.parametres().first().dureeCycle)
    }

    @Test
    fun `la saisie initiale refuse une duree hors limites sans rien ecrire`() = runTest {
        val resultat = depot.configurer(ConfigurationInitiale(d0, null, ParametresCycle(70, 5)), d0)
        assertEquals(ResultatSaisie.Refusee(ErreurSaisie.DureeCycleHorsLimites), resultat)
        assertNull(depot.parametresDeclares().first())
        assertTrue(depot.historique().first().isEmpty())
    }

    @Test
    fun `deplacer des regles de plusieurs jours en une correction`() = runTest {
        depot.ajouter(d0, d0.plusDays(4), d0.plusDays(40))
        val id = depot.historique().first().single().id
        // Corriger le début seul serait refusé : l'ancienne fin précéderait le nouveau début.
        assertEquals(
            ResultatSaisie.Refusee(ErreurSaisie.FinAvantDebut),
            depot.corrigerDebut(id, d0.plusDays(10), d0.plusDays(40)),
        )
        assertEquals(
            ResultatSaisie.Enregistree(null),
            depot.corriger(id, d0.plusDays(10), d0.plusDays(13), d0.plusDays(40)),
        )
        assertEquals(CycleSaisi(id, d0.plusDays(10), d0.plusDays(13)), depot.historique().first().single())
    }

    @Test
    fun `ajouter des regles oubliees recalcule la moyenne`() = runTest {
        // Deux débuts saisis à 64 jours d'écart : un début a été oublié, le cycle est écarté.
        depot.ajouter(d0, null, d0.plusDays(70))
        depot.ajouter(d0.plusDays(64), null, d0.plusDays(70))
        assertEquals(28, etat(d0.plusDays(70)).dureeCycle.jours)
        assertEquals(0, etat(d0.plusDays(70)).dureeCycle.cyclesObserves)

        depot.ajouter(d0.plusDays(32), d0.plusDays(36), d0.plusDays(70))
        // 32 et 32 jours observés, plus la valeur déclarée (28) : 31.
        assertEquals(2, etat(d0.plusDays(70)).dureeCycle.cyclesObserves)
        assertEquals(31, etat(d0.plusDays(70)).dureeCycle.jours)
        assertEquals(
            ResultatSaisie.Refusee(ErreurSaisie.ChevaucheCyclePrecedent),
            depot.ajouter(d0.plusDays(35), null, d0.plusDays(70)),
        )
    }
}
