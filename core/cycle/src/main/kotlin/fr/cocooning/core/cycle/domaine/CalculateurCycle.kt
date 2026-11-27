package fr.cocooning.core.cycle.domaine

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Calcule l'état du cycle à partir des saisies réelles de madame (EF-CYC-03, EF-CYC-04).
 *
 * Principes (cahier des charges, sections 5.1 et 5.2) :
 * - les durées viennent des 6 derniers cycles réels ; tant qu'il y en a moins de 6, la valeur
 *   déclarée à l'installation compte comme un cycle de plus, ce qui fait glisser l'estimation
 *   des valeurs par défaut vers les valeurs réelles au fil des cycles ;
 * - l'ovulation est estimée [DUREE_LUTEALE] jours avant les règles prévues, la phase lutéale
 *   étant plus stable que la phase folliculaire ;
 * - toute prédiction est une fourchette, dont la largeur suit la variabilité observée.
 *
 * Toutes les fonctions sont pures : mêmes entrées, même résultat.
 */
object CalculateurCycle {

    /** Nombre de cycles réels pris en compte (EF-CYC-03). */
    const val FENETRE = 6

    /** Jours entre l'ovulation et les règles suivantes. */
    const val DUREE_LUTEALE = 12

    /** Sans fin saisie au-delà de ce jour du cycle, la fin est considérée comme oubliée. */
    const val SEUIL_FIN_OUBLIEE = 10

    /** Demi-largeur de la fourchette tant que la variabilité n'est pas connue. */
    const val MARGE_PAR_DEFAUT = 2

    /** Demi-largeur maximale de la fourchette. */
    const val MARGE_MAX = 7

    /**
     * Durées de cycle retenues dans les moyennes. En dehors, la saisie est probablement une
     * erreur ou un oubli (un début de règles non saisi double la durée apparente) : le cycle
     * reste dans l'historique mais ne fausse pas les prédictions.
     */
    val CYCLE_PLAUSIBLE = 15..60

    /** Durées de règles retenues dans les moyennes. */
    val REGLES_PLAUSIBLES = 1..15

    /** État du cycle le jour [aujourdhui]. Les cycles saisis après cette date sont ignorés. */
    fun etat(historique: List<CycleSaisi>, parametres: ParametresCycle, aujourdhui: LocalDate): EtatCycle {
        val cycles = historique.filter { !it.debut.isAfter(aujourdhui) }.sortedBy { it.debut }
        val courant = cycles.lastOrNull() ?: return EtatCycle.SansDonnees

        val dureeCycle = estimer(dureesDeCycle(cycles), parametres.dureeCycle.coerceIn(CYCLE_PLAUSIBLE))
        val dureeReglesHabituelle = estimer(
            dureesDeRegles(cycles),
            parametres.dureeRegles.coerceIn(REGLES_PLAUSIBLES),
        )

        val jour = joursEntre(courant.debut, aujourdhui) + 1
        val finSaisie = courant.finRegles?.takeUnless { it.isAfter(aujourdhui) }
        val finOubliee = finSaisie == null && jour > SEUIL_FIN_OUBLIEE
        val reglesEnCours = finSaisie == null && !finOubliee
        val dureeRegles = when {
            finSaisie != null -> joursEntre(courant.debut, finSaisie) + 1
            reglesEnCours -> max(jour, dureeReglesHabituelle.jours)
            else -> dureeReglesHabituelle.jours
        }

        val longueur = dureeCycle.jours.coerceIn(CYCLE_PLAUSIBLE)
        val decoupage = decouper(longueur, dureeRegles)
        val phase = when {
            reglesEnCours -> Phase.Menstruelle
            else -> decoupage.segments.firstOrNull { jour in it }?.phase ?: Phase.Luteale
        }

        val reglesPrevues = courant.debut.plusDays(longueur.toLong())
        val marge = dureeCycle.ecartType?.let { ceil(it).toInt().coerceIn(1, MARGE_MAX) } ?: MARGE_PAR_DEFAUT
        val prochainesRegles = reglesPrevues.minusDays(marge.toLong())..reglesPrevues.plusDays(marge.toLong())
        val ovulation = courant.debut.plusDays((decoupage.jourOvulation - 1).toLong())

        return EtatCycle.Suivi(
            debut = courant.debut,
            jour = jour,
            phase = phase,
            segments = decoupage.segments,
            reglesEnCours = reglesEnCours,
            finOubliee = finOubliee,
            dureeRegles = dureeRegles,
            dureeCycle = dureeCycle,
            dureeReglesHabituelle = dureeReglesHabituelle,
            ovulationEstimee = ovulation,
            fenetreFertile = ovulation.minusDays(5)..ovulation,
            prochainesRegles = prochainesRegles,
            joursDeRetard = max(0, joursEntre(prochainesRegles.endInclusive, aujourdhui)),
            prochainChangement = prochainChangement(
                debut = courant.debut,
                jour = jour,
                aujourdhui = aujourdhui,
                segments = decoupage.segments,
                reglesEnCours = reglesEnCours,
                longueur = longueur,
            ),
        )
    }

    /**
     * Moyenne des [FENETRE] dernières valeurs réelles ; tant qu'il y en a moins, la valeur
     * [declaree] compte comme une valeur de plus. L'écart type ne porte que sur les valeurs réelles.
     */
    fun estimer(observees: List<Int>, declaree: Int): Estimation {
        val reelles = observees.takeLast(FENETRE)
        val valeurs = if (reelles.size >= FENETRE) reelles else reelles + declaree
        return Estimation(
            jours = valeurs.average().roundToInt(),
            ecartType = if (reelles.size >= 2) ecartType(reelles) else null,
            cyclesObserves = reelles.size,
        )
    }

    /** Durées des cycles terminés (du début d'un cycle au début du suivant), du plus ancien au plus récent. */
    fun dureesDeCycle(cycles: List<CycleSaisi>): List<Int> =
        cycles.sortedBy { it.debut }
            .zipWithNext { a, b -> joursEntre(a.debut, b.debut) }
            .filter { it in CYCLE_PLAUSIBLE }

    /** Durées des règles dont la fin a été saisie, du plus ancien au plus récent. */
    fun dureesDeRegles(cycles: List<CycleSaisi>): List<Int> =
        cycles.sortedBy { it.debut }
            .mapNotNull { c -> c.finRegles?.let { joursEntre(c.debut, it) + 1 } }
            .filter { it in REGLES_PLAUSIBLES }

    /**
     * Découpe un cycle de [longueur] jours dont les règles durent [dureeRegles] jours :
     * menstruelle, folliculaire (absente si le cycle est très court), ovulatoire sur 3 jours
     * autour de l'ovulation, lutéale jusqu'à la fin. Les segments se suivent sans trou.
     */
    fun decouper(longueur: Int, dureeRegles: Int): Decoupage {
        val l = longueur.coerceIn(CYCLE_PLAUSIBLE)
        // Au moins 3 jours après les règles : ovulatoire (2 jours minimum) et lutéale (1 jour).
        val r = dureeRegles.coerceIn(1, l - 4)
        val ovulation = max(l - DUREE_LUTEALE, r + 2)
        val segments = buildList {
            add(SegmentPhase(Phase.Menstruelle, 1, r))
            if (ovulation - 2 >= r + 1) add(SegmentPhase(Phase.Folliculaire, r + 1, ovulation - 2))
            add(SegmentPhase(Phase.Ovulatoire, ovulation - 1, ovulation + 1))
            add(SegmentPhase(Phase.Luteale, ovulation + 2, l))
        }
        return Decoupage(segments, ovulation)
    }

    data class Decoupage(val segments: List<SegmentPhase>, val jourOvulation: Int)

    private fun prochainChangement(
        debut: LocalDate,
        jour: Int,
        aujourdhui: LocalDate,
        segments: List<SegmentPhase>,
        reglesEnCours: Boolean,
        longueur: Int,
    ): ChangementPhase? {
        if (reglesEnCours) {
            // La fin n'est pas encore saisie : au plus tôt demain.
            val apres = segments[1]
            val date = maxOf(debut.plusDays((apres.premierJour - 1).toLong()), aujourdhui.plusDays(1))
            return ChangementPhase(date, apres.phase, estime = true)
        }
        segments.firstOrNull { it.premierJour > jour }?.let { suivant ->
            return ChangementPhase(
                date = debut.plusDays((suivant.premierJour - 1).toLong()),
                phase = suivant.phase,
                // Seul le passage en folliculaire découle d'une saisie (la fin des règles).
                estime = suivant.phase != Phase.Folliculaire,
            )
        }
        val reglesPrevues = debut.plusDays(longueur.toLong())
        return if (reglesPrevues.isAfter(aujourdhui)) ChangementPhase(reglesPrevues, Phase.Menstruelle, estime = true) else null
    }

    /** Nombre de jours du [de] au [a] inclus : des règles du 1er au 4 durent 4 jours. */
    fun joursEntreInclus(de: LocalDate, a: LocalDate): Int = joursEntre(de, a) + 1

    internal fun joursEntre(de: LocalDate, a: LocalDate): Int = ChronoUnit.DAYS.between(de, a).toInt()

    private fun ecartType(valeurs: List<Int>): Double {
        val moyenne = valeurs.average()
        return sqrt(valeurs.sumOf { (it - moyenne) * (it - moyenne) } / (valeurs.size - 1))
    }
}
