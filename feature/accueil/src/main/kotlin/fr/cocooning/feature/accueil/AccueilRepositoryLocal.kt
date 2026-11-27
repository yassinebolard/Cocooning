package fr.cocooning.feature.accueil

import fr.cocooning.core.cycle.domaine.CalculateurCycle
import fr.cocooning.core.cycle.domaine.EtatCycle
import fr.cocooning.core.cycle.domaine.OperationCycle
import fr.cocooning.core.cycle.domaine.Phase
import fr.cocooning.core.cycle.donnees.CycleRepository
import fr.cocooning.core.cycle.donnees.ResultatSaisie
import fr.cocooning.core.cycle.donnees.joursCalendrier
import fr.cocooning.designsystem.components.PhaseCycle
import fr.cocooning.designsystem.components.SegmentCycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

/**
 * Accueil avec le vrai cycle de madame, lu dans [cycles] et recalculé chaque jour.
 * Les autres blocs viennent de [contenuFictif] tant que leurs modules n'existent pas.
 *
 * @param jours date du jour, réémise à minuit pour faire avancer le jour J.
 */
class AccueilRepositoryLocal(
    private val cycles: CycleRepository,
    private val horloge: Clock = Clock.systemDefaultZone(),
    private val jours: Flow<LocalDate> = joursCalendrier(horloge),
    private val contenuFictif: (LocalDate) -> DonneesAccueil = ::contenuFictifAccueil,
) : AccueilRepository {

    override fun donnees(): Flow<DonneesAccueil> =
        combine(jours, cycles.historique(), cycles.parametres()) { aujourdhui, historique, parametres ->
            contenuFictif(aujourdhui).copy(
                cycle = CalculateurCycle.etat(historique, parametres, aujourdhui).versResume(),
            )
        }

    override suspend fun basculerRegles(): ResultatBascule {
        val aujourdhui = LocalDate.now(horloge)
        return when (val resultat = cycles.basculerRegles(aujourdhui)) {
            is ResultatSaisie.Enregistree -> when (val operation = resultat.operation) {
                is OperationCycle.Terminer -> {
                    val debut = cycles.historique().first().firstOrNull { it.id == operation.cycleId }?.debut
                    ResultatBascule.Fin(debut?.let { CalculateurCycle.joursEntreInclus(it, operation.fin) } ?: 1)
                }
                is OperationCycle.Rouvrir -> ResultatBascule.Reprise
                is OperationCycle.Commencer, null -> ResultatBascule.Debut
            }
            is ResultatSaisie.Refusee, is ResultatSaisie.Echec -> ResultatBascule.Echec
        }
    }
}

/** Traduit l'état du domaine pour l'accueil et le design système. */
internal fun EtatCycle.versResume(): ResumeCycle = when (this) {
    EtatCycle.SansDonnees -> ResumeCycle.PremiereSaisie
    is EtatCycle.Suivi -> ResumeCycle.Suivi(
        phase = phase.versDesign(),
        jour = jour,
        segments = segments.map { SegmentCycle(it.phase.versDesign(), it.premierJour, it.dernierJour) },
        prochainesRegles = prochainesRegles,
        reglesEnCours = reglesEnCours,
        finOubliee = finOubliee,
        dureeRegles = dureeRegles,
        joursDeRetard = joursDeRetard,
    )
}

private fun Phase.versDesign(): PhaseCycle = when (this) {
    Phase.Menstruelle -> PhaseCycle.Menstruelle
    Phase.Folliculaire -> PhaseCycle.Folliculaire
    Phase.Ovulatoire -> PhaseCycle.Ovulatoire
    Phase.Luteale -> PhaseCycle.Luteale
}
