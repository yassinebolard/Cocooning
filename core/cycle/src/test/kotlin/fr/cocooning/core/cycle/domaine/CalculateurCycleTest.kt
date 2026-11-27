package fr.cocooning.core.cycle.domaine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalculateurCycleTest {

    private val d0: LocalDate = LocalDate.of(2026, 1, 5)
    private val defaut = ParametresCycle()

    /** Cycles réguliers commençant à [d0], de durées [durees], règles de [regles] jours, puis un cycle en cours. */
    private fun historique(durees: List<Int>, regles: Int = 5, finCourant: Int? = regles): List<CycleSaisi> {
        var debut = d0
        val liste = mutableListOf<CycleSaisi>()
        durees.forEachIndexed { i, duree ->
            liste += CycleSaisi(i + 1L, debut, debut.plusDays(regles - 1L))
            debut = debut.plusDays(duree.toLong())
        }
        liste += CycleSaisi(durees.size + 1L, debut, finCourant?.let { debut.plusDays(it - 1L) })
        return liste
    }

    private fun largeur(plage: ClosedRange<LocalDate>) = CalculateurCycle.joursEntre(plage.start, plage.endInclusive)

    private fun debutCourant(h: List<CycleSaisi>) = h.maxOf { it.debut }

    private fun suivi(h: List<CycleSaisi>, jour: Int, p: ParametresCycle = defaut): EtatCycle.Suivi =
        CalculateurCycle.etat(h, p, debutCourant(h).plusDays(jour - 1L)) as EtatCycle.Suivi

    @Test
    fun `sans saisie il n'y a pas de suivi`() {
        assertEquals(EtatCycle.SansDonnees, CalculateurCycle.etat(emptyList(), defaut, d0))
    }

    @Test
    fun `un cycle saisi apres aujourd'hui est ignore`() {
        val h = listOf(CycleSaisi(1, d0.plusDays(3)))
        assertEquals(EtatCycle.SansDonnees, CalculateurCycle.etat(h, defaut, d0))
    }

    @Test
    fun `premier cycle avec les valeurs par defaut`() {
        val h = listOf(CycleSaisi(1, d0, d0.plusDays(4)))
        val e = suivi(h, jour = 22)
        assertEquals(22, e.jour)
        assertEquals(Phase.Luteale, e.phase)
        assertEquals(28, e.dureeCycle.jours)
        assertEquals(0, e.dureeCycle.cyclesObserves)
        assertNull(e.dureeCycle.ecartType)
        // J29 du cycle = 28 jours après le début, fourchette de 2 jours de part et d'autre.
        assertEquals(d0.plusDays(26)..d0.plusDays(30), e.prochainesRegles)
        // Ovulation 12 jours avant les règles prévues : J16.
        assertEquals(d0.plusDays(15), e.ovulationEstimee)
        assertEquals(d0.plusDays(10)..d0.plusDays(15), e.fenetreFertile)
    }

    @Test
    fun `le jour J commence a 1 le jour du debut des regles`() {
        val h = listOf(CycleSaisi(1, d0))
        val e = CalculateurCycle.etat(h, defaut, d0) as EtatCycle.Suivi
        assertEquals(1, e.jour)
        assertEquals(Phase.Menstruelle, e.phase)
        assertTrue(e.reglesEnCours)
    }

    @Test
    fun `des regles de 4 jours au lieu de 6 font passer en folliculaire des J5`() {
        val parametres = ParametresCycle(dureeCycle = 28, dureeRegles = 6)
        val h = listOf(CycleSaisi(1, d0, d0.plusDays(3)))
        assertEquals(Phase.Menstruelle, suivi(h, 4, parametres).phase)
        val e = suivi(h, 5, parametres)
        assertEquals(Phase.Folliculaire, e.phase)
        assertEquals(4, e.dureeRegles)
        assertEquals(SegmentPhase(Phase.Menstruelle, 1, 4), e.segments.first())
        // La durée habituelle s'affine : (4 réels + 6 déclarés) / 2.
        assertEquals(5, e.dureeReglesHabituelle.jours)
    }

    @Test
    fun `des regles de 6 jours restent menstruelles jusqu'a J6`() {
        val h = listOf(CycleSaisi(1, d0, d0.plusDays(5)))
        assertEquals(Phase.Menstruelle, suivi(h, 6).phase)
        assertEquals(Phase.Folliculaire, suivi(h, 7).phase)
        assertEquals(6, suivi(h, 7).dureeRegles)
    }

    @Test
    fun `des regles en cours plus longues que prevu restent menstruelles`() {
        val h = listOf(CycleSaisi(1, d0))
        val e = suivi(h, 7)
        assertTrue(e.reglesEnCours)
        assertEquals(Phase.Menstruelle, e.phase)
        assertEquals(7, e.dureeRegles)
    }

    @Test
    fun `la duree des regles apprend des cycles passes`() {
        // Six cycles avec des règles de 4 jours : la valeur déclarée (6) n'est plus utilisée.
        val h = historique(List(6) { 28 }, regles = 4, finCourant = null)
        val e = suivi(h, 3, ParametresCycle(dureeRegles = 6))
        assertEquals(4, e.dureeReglesHabituelle.jours)
        assertEquals(6, e.dureeReglesHabituelle.cyclesObserves)
    }

    @Test
    fun `la moyenne glisse de la valeur declaree vers les valeurs reelles`() {
        // 1 cycle réel de 32 jours : (32 + 28) / 2.
        assertEquals(30, suivi(historique(listOf(32)), 5).dureeCycle.jours)
        // 3 cycles réels : (3 × 32 + 28) / 4.
        assertEquals(31, suivi(historique(List(3) { 32 }), 5).dureeCycle.jours)
        // 6 cycles réels : la valeur déclarée ne compte plus.
        assertEquals(32, suivi(historique(List(6) { 32 }), 5).dureeCycle.jours)
    }

    @Test
    fun `seuls les 6 derniers cycles comptent`() {
        val e = suivi(historique(listOf(40, 40, 26, 26, 26, 26, 26, 26)), 5)
        assertEquals(26, e.dureeCycle.jours)
        assertEquals(6, e.dureeCycle.cyclesObserves)
    }

    @Test
    fun `cycles courts de 22 jours`() {
        val e = suivi(historique(List(6) { 22 }), 10)
        assertEquals(22, e.dureeCycle.jours)
        assertEquals(
            listOf(
                SegmentPhase(Phase.Menstruelle, 1, 5),
                SegmentPhase(Phase.Folliculaire, 6, 8),
                SegmentPhase(Phase.Ovulatoire, 9, 11),
                SegmentPhase(Phase.Luteale, 12, 22),
            ),
            e.segments,
        )
        assertEquals(Phase.Ovulatoire, e.phase)
        assertEquals(e.debut.plusDays(21)..e.debut.plusDays(23), e.prochainesRegles)
    }

    @Test
    fun `cycle tres court sans place pour la phase folliculaire`() {
        val d = CalculateurCycle.decouper(longueur = 15, dureeRegles = 7)
        assertEquals(
            listOf(
                SegmentPhase(Phase.Menstruelle, 1, 7),
                SegmentPhase(Phase.Ovulatoire, 8, 10),
                SegmentPhase(Phase.Luteale, 11, 15),
            ),
            d.segments,
        )
    }

    @Test
    fun `cycles longs de 40 jours`() {
        val e = suivi(historique(List(6) { 40 }), 20)
        assertEquals(40, e.dureeCycle.jours)
        assertEquals(Phase.Folliculaire, e.phase)
        // Ovulation à J28 (40 - 12).
        assertEquals(e.debut.plusDays(27), e.ovulationEstimee)
        assertEquals(Phase.Ovulatoire, suivi(historique(List(6) { 40 }), 28).phase)
        assertEquals(Phase.Luteale, suivi(historique(List(6) { 40 }), 30).phase)
    }

    @Test
    fun `les segments se suivent sans trou pour toutes les durees`() {
        for (longueur in 1..80) {
            for (regles in 0..20) {
                val segments = CalculateurCycle.decouper(longueur, regles).segments
                val l = longueur.coerceIn(CalculateurCycle.CYCLE_PLAUSIBLE)
                assertEquals(1, segments.first().premierJour)
                assertEquals(l, segments.last().dernierJour)
                segments.zipWithNext { a, b -> assertEquals("$longueur/$regles", a.dernierJour + 1, b.premierJour) }
                segments.forEach { assertTrue("$longueur/$regles", it.premierJour <= it.dernierJour) }
                assertEquals(Phase.Luteale, segments.last().phase)
            }
        }
    }

    @Test
    fun `un cycle aberrant ne fausse pas la moyenne`() {
        // Un début de règles non saisi donne un cycle apparent de 56 jours, puis 70 jours.
        assertEquals(listOf(28, 56, 28), CalculateurCycle.dureesDeCycle(historique(listOf(28, 56, 28))))
        assertEquals(listOf(28, 28), CalculateurCycle.dureesDeCycle(historique(listOf(28, 70, 28))))
        assertEquals(listOf(28), CalculateurCycle.dureesDeCycle(historique(listOf(28, 10))))
    }

    @Test
    fun `fin oubliee apres le seuil`() {
        val h = listOf(CycleSaisi(1, d0))
        val avant = suivi(h, CalculateurCycle.SEUIL_FIN_OUBLIEE)
        assertTrue(avant.reglesEnCours)
        assertFalse(avant.finOubliee)

        val apres = suivi(h, CalculateurCycle.SEUIL_FIN_OUBLIEE + 2)
        assertFalse(apres.reglesEnCours)
        assertTrue(apres.finOubliee)
        // Durée estimée avec la valeur habituelle, la phase suit le calendrier.
        assertEquals(5, apres.dureeRegles)
        assertEquals(Phase.Folliculaire, apres.phase)
    }

    @Test
    fun `une fin oubliee n'entre pas dans la duree habituelle des regles`() {
        val h = listOf(
            CycleSaisi(1, d0, d0.plusDays(3)),
            CycleSaisi(2, d0.plusDays(28), null),
            CycleSaisi(3, d0.plusDays(56), null),
        )
        assertEquals(listOf(4), CalculateurCycle.dureesDeRegles(h))
    }

    @Test
    fun `la fourchette suit la variabilite`() {
        // Écart type ≈ 2,16 : marge de 3 jours.
        val variable = suivi(historique(listOf(26, 30, 28, 32, 27, 29)), 5)
        val prevu = variable.debut.plusDays(variable.dureeCycle.jours.toLong())
        assertEquals(prevu.minusDays(3)..prevu.plusDays(3), variable.prochainesRegles)

        // Cycles parfaitement réguliers : la fourchette garde au moins un jour de chaque côté.
        val regulier = suivi(historique(List(6) { 28 }), 5)
        assertEquals(2, largeur(regulier.prochainesRegles))

        // Très irréguliers : la marge est plafonnée.
        val irregulier = suivi(historique(listOf(20, 45, 22, 50, 21, 44)), 5)
        assertEquals(2 * CalculateurCycle.MARGE_MAX, largeur(irregulier.prochainesRegles))
    }

    @Test
    fun `retard compte apres la fin de la fourchette et le jour J continue`() {
        val h = listOf(CycleSaisi(1, d0, d0.plusDays(4)))
        // Fourchette jusqu'au 30e jour après le début, soit J31.
        assertEquals(0, suivi(h, 31).joursDeRetard)
        val e = suivi(h, 35)
        assertEquals(35, e.jour)
        assertEquals(4, e.joursDeRetard)
        assertEquals(Phase.Luteale, e.phase)
        assertNull(e.prochainChangement)
    }

    @Test
    fun `prochain changement de phase pour les notifications`() {
        val enCours = suivi(listOf(CycleSaisi(1, d0)), 2)
        assertEquals(ChangementPhase(d0.plusDays(5), Phase.Folliculaire, estime = true), enCours.prochainChangement)

        // Règles qui durent plus que prévu : le changement est au plus tôt demain.
        val prolongees = suivi(listOf(CycleSaisi(1, d0)), 7)
        assertEquals(d0.plusDays(7), prolongees.prochainChangement!!.date)

        val folliculaire = suivi(listOf(CycleSaisi(1, d0, d0.plusDays(4))), 8)
        assertEquals(ChangementPhase(d0.plusDays(14), Phase.Ovulatoire, estime = true), folliculaire.prochainChangement)

        val luteale = suivi(listOf(CycleSaisi(1, d0, d0.plusDays(4))), 20)
        assertEquals(ChangementPhase(d0.plusDays(28), Phase.Menstruelle, estime = true), luteale.prochainChangement)
    }

    @Test
    fun `corriger le debut deplace le jour J`() {
        val aujourdhui = d0.plusDays(9)
        val avant = CalculateurCycle.etat(listOf(CycleSaisi(1, d0, d0.plusDays(4))), defaut, aujourdhui) as EtatCycle.Suivi
        val apres = CalculateurCycle.etat(
            listOf(CycleSaisi(1, d0.minusDays(2), d0.plusDays(4))),
            defaut,
            aujourdhui,
        ) as EtatCycle.Suivi
        assertEquals(10, avant.jour)
        assertEquals(12, apres.jour)
        assertEquals(7, apres.dureeRegles)
    }
}
