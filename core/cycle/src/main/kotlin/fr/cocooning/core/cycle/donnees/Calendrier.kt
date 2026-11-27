package fr.cocooning.core.cycle.donnees

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Émet la date du jour, puis de nouveau juste après chaque minuit : le jour J avance tout seul. */
fun joursCalendrier(horloge: Clock = Clock.systemDefaultZone()): Flow<LocalDate> = flow {
    while (true) {
        val maintenant = LocalDateTime.now(horloge)
        emit(maintenant.toLocalDate())
        val minuit = maintenant.toLocalDate().plusDays(1).atStartOfDay()
        delay(Duration.between(maintenant, minuit).toMillis() + 1_000)
    }
}.distinctUntilChanged()
