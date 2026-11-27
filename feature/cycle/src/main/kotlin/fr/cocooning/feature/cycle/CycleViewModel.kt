package fr.cocooning.feature.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.cocooning.core.cycle.domaine.Bilan
import fr.cocooning.core.cycle.domaine.BilanCycles
import fr.cocooning.core.cycle.domaine.CalculateurCycle
import fr.cocooning.core.cycle.domaine.EtatCycle
import fr.cocooning.core.cycle.domaine.ParametresCycle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** États de l'onglet Cycle. */
sealed interface CycleUiState {
    data object Chargement : CycleUiState

    /** Madame n'a pas encore fait la saisie du premier lancement (EF-CYC-02). */
    data object PremiereSaisie : CycleUiState

    /**
     * @param etat état du cycle aujourd'hui.
     * @param bilan historique et moyennes, recalculés à chaque correction.
     */
    data class Pret(
        val aujourdhui: LocalDate,
        val etat: EtatCycle,
        val bilan: BilanCycles,
        val parametres: ParametresCycle,
    ) : CycleUiState

    data class Erreur(val message: String) : CycleUiState
}

/** Vue d'ensemble du cycle : état du jour, moyennes et historique à corriger. */
class CycleViewModel(dependances: DependancesCycle) : ViewModel() {

    val etat: StateFlow<CycleUiState> = combine(
        dependances.jours,
        dependances.depot.historique(),
        dependances.depot.parametresDeclares(),
    ) { aujourdhui, historique, declares ->
        if (declares == null) {
            CycleUiState.PremiereSaisie
        } else {
            CycleUiState.Pret(
                aujourdhui = aujourdhui,
                etat = CalculateurCycle.etat(historique, declares, aujourdhui),
                bilan = Bilan.calculer(historique, declares, aujourdhui),
                parametres = declares,
            )
        }
    }
        .catch { emit(CycleUiState.Erreur("Ton suivi n'a pas pu se charger. Réessaie dans un instant.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CycleUiState.Chargement)

    companion object {
        fun fabrique(dependances: DependancesCycle): ViewModelProvider.Factory = viewModelFactory {
            initializer { CycleViewModel(dependances) }
        }
    }
}
