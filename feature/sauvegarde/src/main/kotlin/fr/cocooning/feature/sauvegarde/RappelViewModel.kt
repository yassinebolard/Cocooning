package fr.cocooning.feature.sauvegarde

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Rappel d'export affiché sur l'accueil. [visible] faux : rien à afficher. */
data class EtatRappel(val visible: Boolean = false, val dernierExport: LocalDate? = null)

class RappelViewModel(private val dependances: DependancesSauvegarde) : ViewModel() {

    val etat: StateFlow<EtatRappel> =
        combine(dependances.suivi.dernierExport, dependances.suivi.rappelReporteJusquau) { dernier, reporte ->
            EtatRappel(visible = RappelExport.estDu(dernier, reporte, aujourdhui()), dernierExport = dernier)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatRappel())

    /** « Plus tard » : le rappel revient dans [RappelExport.REPORT_JOURS] jours. */
    fun reporter() {
        viewModelScope.launch { dependances.suivi.reporterRappel(aujourdhui().plusDays(RappelExport.REPORT_JOURS)) }
    }

    private fun aujourdhui(): LocalDate = LocalDate.now(dependances.horloge)

    companion object {
        fun fabrique(dependances: DependancesSauvegarde): ViewModelProvider.Factory = viewModelFactory {
            initializer { RappelViewModel(dependances) }
        }
    }
}
