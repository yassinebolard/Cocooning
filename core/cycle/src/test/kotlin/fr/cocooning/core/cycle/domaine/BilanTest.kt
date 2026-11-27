package fr.cocooning.core.cycle.domaine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BilanTest {

    private val d0: LocalDate = LocalDate.of(2026, 1, 5)

    /** Cycles qui commencent aux décalages [jours] depuis d0, règles de 5 jours sauf le dernier. */
    private fun cycles(vararg jours: Long) = jours.mapIndexed { i, j ->
        val debut = d0.plusDays(j)
        CycleSaisi(i + 1L, debut, if (i < jours.lastIndex) debut.plusDays(4) else null)
    }

    @Test
    fun `durees et cycle en cours, du plus recent au plus ancien`() {
        val bilan = Bilan.calculer(cycles(0, 28, 58), ParametresCycle(), d0.plusDays(60))
        assertEquals(listOf(3L, 2L, 1L), bilan.cycles.map { it.cycle.id })
        assertNull(bilan.cycles[0].dureeCycle)
        assertNull(bilan.cycles[0].dureeRegles)
        assertEquals(30, bilan.cycles[1].dureeCycle)
        assertEquals(28, bilan.cycles[2].dureeCycle)
        assertEquals(5, bilan.cycles[2].dureeRegles)
        // (28 + 30 + 28 déclarés) / 3.
        assertEquals(29, bilan.dureeCycle.jours)
    }

    @Test
    fun `un cycle invraisemblable n'est pas retenu`() {
        val bilan = Bilan.calculer(cycles(0, 70, 98), ParametresCycle(), d0.plusDays(100))
        val long = bilan.cycles.single { it.dureeCycle == 70 }
        assertFalse(long.cycleRetenu)
        assertTrue(bilan.cycles.single { it.dureeCycle == 28 }.cycleRetenu)
    }

    @Test
    fun `seuls les six derniers cycles comptent`() {
        val debuts = LongArray(9) { it * 28L }
        val bilan = Bilan.calculer(cycles(*debuts), ParametresCycle(), d0.plusDays(9 * 28L))
        val retenus = bilan.cycles.filter { it.cycleRetenu }
        assertEquals(6, retenus.size)
        // Les plus anciens (en fin de liste) ne sont plus retenus.
        assertFalse(bilan.cycles.last().cycleRetenu)
        assertEquals(6, bilan.dureeCycle.cyclesObserves)
    }

    @Test
    fun `les cycles futurs sont ignores`() {
        val bilan = Bilan.calculer(cycles(0, 28), ParametresCycle(), d0.plusDays(10))
        assertEquals(1, bilan.cycles.size)
    }
}
