package fr.cocooning.core.cycle.domaine

import java.time.LocalDate

/**
 * Un cycle de l'historique avec ce qui s'en déduit, pour l'écran de correction des dates.
 *
 * @param dureeCycle du début de ce cycle au début du suivant, nulle pour le cycle en cours.
 * @param dureeRegles du début à la fin saisie, inclus, nulle tant que la fin n'est pas saisie.
 * @param cycleRetenu vrai quand [dureeCycle] entre dans la moyenne : durée plausible et parmi
 * les [CalculateurCycle.FENETRE] dernières.
 * @param reglesRetenues même chose pour [dureeRegles].
 */
data class CyclePasse(
    val cycle: CycleSaisi,
    val dureeCycle: Int?,
    val dureeRegles: Int?,
    val cycleRetenu: Boolean,
    val reglesRetenues: Boolean,
)

/**
 * Historique et moyennes, recalculés à chaque correction.
 *
 * @param cycles du plus récent au plus ancien.
 */
data class BilanCycles(
    val cycles: List<CyclePasse>,
    val dureeCycle: Estimation,
    val dureeRegles: Estimation,
)

object Bilan {

    /** Bilan des cycles commencés au plus tard le [aujourdhui]. */
    fun calculer(historique: List<CycleSaisi>, parametres: ParametresCycle, aujourdhui: LocalDate): BilanCycles {
        val cycles = historique.filter { !it.debut.isAfter(aujourdhui) }.sortedBy { it.debut }
        val dureesCycle = cycles.zipWithNext { a, b -> CalculateurCycle.joursEntre(a.debut, b.debut) } + listOf(null)
        val dureesRegles = cycles.map { c -> c.finRegles?.let { CalculateurCycle.joursEntreInclus(c.debut, it) } }

        val cyclesRetenus = derniersPlausibles(dureesCycle, CalculateurCycle.CYCLE_PLAUSIBLE)
        val reglesRetenues = derniersPlausibles(dureesRegles, CalculateurCycle.REGLES_PLAUSIBLES)

        val passes = cycles.mapIndexed { i, cycle ->
            CyclePasse(
                cycle = cycle,
                dureeCycle = dureesCycle[i],
                dureeRegles = dureesRegles[i],
                cycleRetenu = i in cyclesRetenus,
                reglesRetenues = i in reglesRetenues,
            )
        }
        return BilanCycles(
            cycles = passes.reversed(),
            dureeCycle = CalculateurCycle.estimer(
                CalculateurCycle.dureesDeCycle(cycles),
                parametres.dureeCycle.coerceIn(CalculateurCycle.CYCLE_PLAUSIBLE),
            ),
            dureeRegles = CalculateurCycle.estimer(
                CalculateurCycle.dureesDeRegles(cycles),
                parametres.dureeRegles.coerceIn(CalculateurCycle.REGLES_PLAUSIBLES),
            ),
        )
    }

    /** Indices des [CalculateurCycle.FENETRE] dernières valeurs comprises dans [plage]. */
    private fun derniersPlausibles(valeurs: List<Int?>, plage: IntRange): Set<Int> =
        valeurs.indices.filter { valeurs[it]?.let { v -> v in plage } == true }
            .takeLast(CalculateurCycle.FENETRE)
            .toSet()
}
