package fr.cocooning.feature.cycle

import androidx.navigation3.runtime.NavKey
import fr.cocooning.core.cycle.donnees.CycleRepository
import fr.cocooning.core.cycle.donnees.joursCalendrier
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import kotlinx.serialization.Serializable

/** Onglet Cycle, troisième onglet de la barre. */
@Serializable
data object CycleCle : NavKey

/** Correction des dates des règles du cycle [cycleId], au-dessus de l'onglet Cycle. */
@Serializable
data class CorrectionCycleCle(val cycleId: Long) : NavKey

/** Ajout de règles passées oubliées. */
@Serializable
data object AjoutReglesCle : NavKey

/** Modification des durées habituelles et de la contraception déclarées (EF-CYC-02). */
@Serializable
data object ReglagesCycleCle : NavKey

/**
 * Ce dont les écrans du cycle ont besoin, fourni par l'application.
 *
 * @param jours date du jour, réémise à minuit.
 */
class DependancesCycle(
    val depot: CycleRepository,
    val horloge: Clock = Clock.systemDefaultZone(),
    val jours: Flow<LocalDate> = joursCalendrier(horloge),
)
