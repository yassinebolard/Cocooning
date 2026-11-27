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

    /** Enregistre le début ou la fin des règles, puis transmet à [annoncer] ce qui a été noté. */
    fun basculerRegles(annoncer: (String) -> Unit = {}) {
        viewModelScope.launch { annoncer(annonce(depot.basculerRegles())) }
    }

    companion object {
        fun fabrique(depot: AccueilRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AccueilViewModel(depot) }
        }
    }
}

/** Phrase lue par la barre de message et TalkBack après un appui sur le bouton des règles. */
internal fun annonce(resultat: ResultatBascule): String = when (resultat) {
    ResultatBascule.Debut -> "C'est noté : début des règles aujourd'hui, jour 1 du cycle."
    is ResultatBascule.Fin ->
        if (resultat.dureeJours == 1) "C'est noté : fin des règles aujourd'hui, après 1 jour."
        else "C'est noté : fin des règles aujourd'hui, après ${resultat.dureeJours} jours."
    ResultatBascule.Reprise -> "C'est noté : tes règles continuent."
    ResultatBascule.Echec -> "Ça n'a pas pu être enregistré. Réessaie dans un instant."
}
