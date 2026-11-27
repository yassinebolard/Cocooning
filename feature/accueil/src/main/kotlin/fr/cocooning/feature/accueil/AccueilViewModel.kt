package fr.cocooning.feature.accueil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Expose l'état de l'accueil et reçoit ses actions (flux unidirectionnel). */
class AccueilViewModel(private val depot: AccueilRepository) : ViewModel() {

    val etat: StateFlow<AccueilUiState> = depot.donnees()
        .map<DonneesAccueil, AccueilUiState> { AccueilUiState.Pret(it) }
        .catch { emit(AccueilUiState.Erreur("L'accueil n'a pas pu se charger. Réessaie dans un instant.")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AccueilUiState.Chargement,
        )

    fun basculerRegles() {
        viewModelScope.launch { depot.basculerRegles() }
    }

    companion object {
        fun fabrique(depot: AccueilRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AccueilViewModel(depot) }
        }
    }
}
