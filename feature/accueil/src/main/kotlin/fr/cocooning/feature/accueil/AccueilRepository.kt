package fr.cocooning.feature.accueil

import kotlinx.coroutines.flow.Flow

/** Source des données de l'accueil. La version réelle viendra avec la couche de données. */
interface AccueilRepository {
    /** Émet les données à jour à chaque changement. */
    fun donnees(): Flow<DonneesAccueil>

    /** Madame indique en un geste le début ou la fin de ses règles (EF-CYC-01). */
    suspend fun basculerRegles()
}
