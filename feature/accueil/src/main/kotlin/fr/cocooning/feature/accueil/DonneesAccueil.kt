package fr.cocooning.feature.accueil

import fr.cocooning.designsystem.components.PhaseCycle
import fr.cocooning.designsystem.components.SegmentCycle
import java.time.LocalDate
import java.time.LocalTime

/**
 * Ce que l'accueil affiche, assemblé à partir des autres modules (cycle, messages, agenda,
 * notes). Chaque bloc est facultatif : un bloc absent n'est pas affiché.
 */
data class DonneesAccueil(
    val prenom: String,
    val prenomPartenaire: String,
    val aujourdhui: LocalDate,
    val cycle: ResumeCycle?,
    val dernierMessage: ApercuMessage?,
    val prochainEvenement: ApercuEvenement?,
    val notesEpinglees: List<ApercuNote>,
)

/**
 * Résumé du cycle du jour. Présent seulement pour madame, ou pour monsieur si elle partage
 * sa phase (EF-CYC-12).
 */
sealed interface ResumeCycle {

    /** Aucune règle saisie : l'accueil invite à indiquer le premier jour des prochaines règles. */
    data object PremiereSaisie : ResumeCycle

    /**
     * @param jour jour J du cycle, J1 étant le premier jour des règles.
     * @param reglesEnCours vrai entre le début et la fin des règles saisis (EF-CYC-01).
     * @param finOubliee la fin des règles n'a pas été indiquée : sa durée est estimée à [dureeRegles] jours.
     * @param prochainesRegles fourchette de dates, jamais une date seule (EF-CYC-04).
     * @param joursDeRetard jours écoulés après la fourchette, 0 sinon.
     */
    data class Suivi(
        val phase: PhaseCycle,
        val jour: Int,
        val segments: List<SegmentCycle>,
        val prochainesRegles: ClosedRange<LocalDate>,
        val reglesEnCours: Boolean,
        val finOubliee: Boolean = false,
        val dureeRegles: Int = 5,
        val joursDeRetard: Int = 0,
    ) : ResumeCycle
}

/** Ce qu'a fait le bouton des règles, pour l'annoncer. */
sealed interface ResultatBascule {
    data object Debut : ResultatBascule

    data class Fin(val dureeJours: Int) : ResultatBascule

    /** Fin saisie aujourd'hui puis bouton touché de nouveau : les règles continuent. */
    data object Reprise : ResultatBascule

    data object Echec : ResultatBascule
}

data class ApercuMessage(
    val auteur: String,
    val heure: LocalTime,
    val texte: String,
    val nonLus: Int,
)

data class ApercuEvenement(
    val titre: String,
    val jour: LocalDate,
    val heure: LocalTime?,
    val lieu: String?,
)

data class ApercuNote(
    val id: String,
    val titre: String,
    val extrait: String,
)
