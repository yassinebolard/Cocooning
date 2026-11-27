package fr.cocooning.feature.accueil

/** États de l'écran d'accueil. */
sealed interface AccueilUiState {
    data object Chargement : AccueilUiState

    data class Pret(val donnees: DonneesAccueil) : AccueilUiState

    data class Erreur(val message: String) : AccueilUiState
}
