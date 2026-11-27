package fr.cocooning.feature.sauvegarde

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * [SuiviSauvegarde] dans les préférences chiffrées de l'appli. Ces valeurs partent dans la
 * sauvegarde avec le reste des préférences.
 *
 * @param donneesDejaPresentes vrai si le téléphone a déjà des données : une installation mise à
 *   jour ne doit pas revoir l'accueil du premier lancement.
 */
class SuiviSauvegardePreferences(
    private val magasin: DataStore<Preferences>,
    private val donneesDejaPresentes: () -> Boolean = { false },
) : SuiviSauvegarde {

    override val dernierExport: Flow<LocalDate?> =
        magasin.data.map { it[DERNIER_EXPORT]?.let(LocalDate::ofEpochDay) }.distinctUntilChanged()

    override val rappelReporteJusquau: Flow<LocalDate?> =
        magasin.data.map { it[RAPPEL_REPORTE]?.let(LocalDate::ofEpochDay) }.distinctUntilChanged()

    override val premierLancementTermine: Flow<Boolean> =
        magasin.data.map { it[PREMIER_LANCEMENT_TERMINE] == true || donneesDejaPresentes() }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    override suspend fun noterExport(jour: LocalDate) {
        magasin.edit {
            it[DERNIER_EXPORT] = jour.toEpochDay()
            it.remove(RAPPEL_REPORTE)
        }
    }

    override suspend fun reporterRappel(jusquau: LocalDate) {
        magasin.edit { it[RAPPEL_REPORTE] = jusquau.toEpochDay() }
    }

    override suspend fun terminerPremierLancement() {
        magasin.edit { it[PREMIER_LANCEMENT_TERMINE] = true }
    }

    private companion object {
        val DERNIER_EXPORT = longPreferencesKey("sauvegarde_dernier_export_jour")
        val RAPPEL_REPORTE = longPreferencesKey("sauvegarde_rappel_reporte_jour")
        val PREMIER_LANCEMENT_TERMINE = booleanPreferencesKey("premier_lancement_termine")
    }
}
