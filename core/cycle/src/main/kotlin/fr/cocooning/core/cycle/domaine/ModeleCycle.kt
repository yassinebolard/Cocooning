package fr.cocooning.core.cycle.domaine

import java.time.LocalDate

/*
 * Modèle du cycle, sans dépendance à Android ni à l'interface.
 *
 * Les données du cycle appartiennent à madame et restent sur son téléphone (règle de consentement,
 * EF-CYC-12). Monsieur ne recevra qu'un résumé filtré selon ce qu'elle partage ; ce résumé sera
 * construit à partir de [EtatCycle], jamais à partir de l'historique brut.
 */

/** Les quatre phases du cycle (cahier des charges, section 5.2). */
enum class Phase { Menstruelle, Folliculaire, Ovulatoire, Luteale }

/**
 * Un cycle tel que madame l'a saisi : le premier jour des règles et, si elle l'a indiqué,
 * le dernier jour des règles. La durée du cycle n'est pas stockée : elle se déduit du début
 * du cycle suivant.
 */
data class CycleSaisi(
    val id: Long,
    val debut: LocalDate,
    val finRegles: LocalDate? = null,
)

/**
 * Valeurs déclarées par madame à l'installation (EF-CYC-02), utilisées tant que l'historique
 * ne suffit pas. Par défaut, un cycle de 28 jours et des règles de 5 jours.
 */
data class ParametresCycle(
    val dureeCycle: Int = DUREE_CYCLE_PAR_DEFAUT,
    val dureeRegles: Int = DUREE_REGLES_PAR_DEFAUT,
) {
    companion object {
        const val DUREE_CYCLE_PAR_DEFAUT = 28
        const val DUREE_REGLES_PAR_DEFAUT = 5
    }
}

/** Une phase sur les jours du cycle, du jour [premierJour] au jour [dernierJour] inclus (J1 = début des règles). */
data class SegmentPhase(val phase: Phase, val premierJour: Int, val dernierJour: Int) {
    operator fun contains(jour: Int): Boolean = jour in premierJour..dernierJour
}

/**
 * Une valeur estimée à partir de l'historique.
 *
 * @param jours valeur retenue, arrondie au jour.
 * @param ecartType variabilité des cycles observés, nulle tant qu'il y en a moins de deux.
 * @param cyclesObserves nombre de valeurs réelles prises en compte (au plus 6, EF-CYC-03).
 */
data class Estimation(val jours: Int, val ecartType: Double?, val cyclesObserves: Int)

/**
 * Prochain changement de phase prévu. Sert à préparer les notifications de phase à monsieur
 * (EF-NTF-01), qui ne seront envoyées que si madame partage sa phase (EF-CYC-12).
 *
 * @param estime vrai quand la date dépend d'une prédiction (début des prochaines règles),
 * faux quand elle découle des jours déjà saisis.
 */
data class ChangementPhase(val date: LocalDate, val phase: Phase, val estime: Boolean)

/** Ce que l'application sait du cycle à une date donnée. */
sealed interface EtatCycle {

    /** Aucun cycle saisi : le suivi commencera au premier « Mes règles ont commencé ». */
    data object SansDonnees : EtatCycle

    /**
     * @param jour jour J du cycle en cours, J1 étant le premier jour des règles. Il continue
     * de compter au-delà de la durée prévue en cas de retard.
     * @param reglesEnCours vrai entre le début saisi et la fin saisie, tant que la fin n'est pas
     * considérée comme oubliée.
     * @param finOubliee vrai quand aucune fin n'a été saisie depuis plus de
     * [CalculateurCycle.SEUIL_FIN_OUBLIEE] jours : la durée des règles est alors estimée.
     * @param dureeRegles durée des règles retenue pour ce cycle : la durée saisie si la fin
     * l'est, sinon l'estimation (ou le nombre de jours écoulés si les règles durent plus longtemps).
     * @param prochainesRegles fourchette, jamais une date seule (EF-CYC-04).
     * @param joursDeRetard jours écoulés après la fin de [prochainesRegles], 0 sinon.
     */
    data class Suivi(
        val debut: LocalDate,
        val jour: Int,
        val phase: Phase,
        val segments: List<SegmentPhase>,
        val reglesEnCours: Boolean,
        val finOubliee: Boolean,
        val dureeRegles: Int,
        val dureeCycle: Estimation,
        val dureeReglesHabituelle: Estimation,
        val ovulationEstimee: LocalDate,
        val fenetreFertile: ClosedRange<LocalDate>,
        val prochainesRegles: ClosedRange<LocalDate>,
        val joursDeRetard: Int,
        val prochainChangement: ChangementPhase?,
    ) : EtatCycle
}
