package fr.cocooning.feature.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.cocooning.core.cycle.domaine.CalculateurCycle
import fr.cocooning.core.cycle.domaine.CycleSaisi
import fr.cocooning.core.cycle.donnees.ResultatSaisie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Formulaire de correction des dates d'un cycle, ou d'ajout de règles oubliées.
 *
 * @param origine le cycle tel qu'il est enregistré, nul pour un ajout.
 * @param introuvable vrai quand le cycle a été supprimé entre-temps.
 * @param termine phrase à annoncer une fois l'opération faite ; l'écran se referme alors.
 */
data class FormulaireCorrection(
    val charge: Boolean = false,
    val aujourdhui: LocalDate,
    val origine: CycleSaisi? = null,
    val introuvable: Boolean = false,
    val debut: LocalDate? = null,
    val fin: LocalDate? = null,
    val erreur: String? = null,
    val enregistrement: Boolean = false,
    val termine: String? = null,
) {
    val ajout: Boolean get() = origine == null

    /** Durée des règles avec les dates choisies, nulle sans fin ou si la fin précède le début. */
    val dureeRegles: Int?
        get() {
            val d = debut ?: return null
            val f = fin ?: return null
            return if (f.isBefore(d)) null else CalculateurCycle.joursEntreInclus(d, f)
        }

    val modifie: Boolean get() = origine == null || origine.debut != debut || origine.finRegles != fin
}

/** @param cycleId cycle à corriger, nul pour ajouter des règles oubliées. */
class CorrectionViewModel(
    private val dependances: DependancesCycle,
    private val cycleId: Long?,
) : ViewModel() {

    private val depot = dependances.depot

    private val _formulaire = MutableStateFlow(FormulaireCorrection(aujourdhui = aujourdhui()))
    val formulaire: StateFlow<FormulaireCorrection> = _formulaire.asStateFlow()

    init {
        viewModelScope.launch {
            val cycle = cycleId?.let { id -> depot.historique().first().firstOrNull { it.id == id } }
            _formulaire.update {
                it.copy(
                    charge = true,
                    origine = cycle,
                    introuvable = cycleId != null && cycle == null,
                    debut = cycle?.debut,
                    fin = cycle?.finRegles,
                )
            }
        }
    }

    fun choisirDebut(date: LocalDate) = _formulaire.update { it.copy(debut = date, erreur = null) }

    fun choisirFin(date: LocalDate) = _formulaire.update { it.copy(fin = date, erreur = null) }

    fun effacerFin() = _formulaire.update { it.copy(fin = null, erreur = null) }

    fun enregistrer() {
        val f = _formulaire.value
        val debut = f.debut
        if (f.enregistrement) return
        if (debut == null) {
            _formulaire.update { it.copy(erreur = "Choisis le premier jour des règles.") }
            return
        }
        lancer {
            val origine = f.origine
            val resultat = if (origine == null) {
                depot.ajouter(debut, f.fin, aujourdhui())
            } else {
                depot.corriger(origine.id, debut, f.fin, aujourdhui())
            }
            resultat to if (origine == null) "C'est noté : règles ajoutées." else "C'est corrigé. Tes moyennes sont à jour."
        }
    }

    fun supprimer() {
        val origine = _formulaire.value.origine ?: return
        lancer { depot.supprimer(origine.id) to "Ces règles sont supprimées. Tes moyennes sont à jour." }
    }

    private fun lancer(operation: suspend () -> Pair<ResultatSaisie, String>) {
        if (_formulaire.value.enregistrement) return
        _formulaire.update { it.copy(enregistrement = true, erreur = null) }
        viewModelScope.launch {
            val (resultat, annonce) = operation()
            val erreur = erreurDe(resultat)
            _formulaire.update {
                it.copy(enregistrement = false, erreur = erreur, termine = if (erreur == null) annonce else null)
            }
        }
    }

    private fun aujourdhui(): LocalDate = LocalDate.now(dependances.horloge)

    companion object {
        fun fabrique(dependances: DependancesCycle, cycleId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer { CorrectionViewModel(dependances, cycleId) }
        }
    }
}
