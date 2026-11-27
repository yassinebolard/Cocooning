package fr.cocooning.feature.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.cocooning.core.cycle.domaine.CalculateurCycle
import fr.cocooning.core.cycle.domaine.ConfigurationInitiale
import fr.cocooning.core.cycle.domaine.Contraception
import fr.cocooning.core.cycle.domaine.ParametresCycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Formulaire de la saisie du premier lancement (EF-CYC-02), qui sert aussi à modifier les durées
 * habituelles et la contraception plus tard.
 *
 * @param demanderDate vrai tant que l'historique est vide : on demande alors les dernières règles.
 * @param modification vrai quand madame revient modifier ses réglages.
 * @param enregistre vrai une fois la saisie écrite : l'écran se referme.
 */
data class FormulaireConfiguration(
    val charge: Boolean = false,
    val aujourdhui: LocalDate,
    val modification: Boolean = false,
    val demanderDate: Boolean = true,
    val dernieresRegles: LocalDate? = null,
    val reglesEnCours: Boolean = false,
    val finDernieresRegles: LocalDate? = null,
    val dureeCycle: Int = ParametresCycle.DUREE_CYCLE_PAR_DEFAUT,
    val dureeRegles: Int = ParametresCycle.DUREE_REGLES_PAR_DEFAUT,
    val contraception: Contraception? = null,
    val erreur: String? = null,
    val enregistrement: Boolean = false,
    val enregistre: Boolean = false,
) {
    /** On demande le dernier jour seulement si les règles ont pu se terminer avant aujourd'hui. */
    val demanderFin: Boolean get() = demanderDate && dernieresRegles != null && dernieresRegles.isBefore(aujourdhui)

    val plageCycle: IntRange get() = CalculateurCycle.CYCLE_PLAUSIBLE
    val plageRegles: IntRange get() = CalculateurCycle.REGLES_PLAUSIBLES
}

class ConfigurationViewModel(private val dependances: DependancesCycle) : ViewModel() {

    private val depot = dependances.depot

    private val _formulaire = MutableStateFlow(FormulaireConfiguration(aujourdhui = aujourdhui()))
    val formulaire: StateFlow<FormulaireConfiguration> = _formulaire.asStateFlow()

    init {
        viewModelScope.launch {
            val declares = depot.parametresDeclares().first()
            val historiqueVide = depot.historique().first().isEmpty()
            _formulaire.update {
                it.copy(
                    charge = true,
                    modification = declares != null,
                    demanderDate = historiqueVide,
                    dureeCycle = declares?.dureeCycle ?: it.dureeCycle,
                    dureeRegles = declares?.dureeRegles ?: it.dureeRegles,
                    contraception = declares?.contraception,
                )
            }
        }
    }

    fun choisirDernieresRegles(date: LocalDate) = _formulaire.update {
        val fin = it.finDernieresRegles?.takeUnless { f -> f.isBefore(date) }
        it.copy(dernieresRegles = date, finDernieresRegles = fin, erreur = null)
    }

    fun indiquerReglesEnCours(enCours: Boolean) =
        _formulaire.update { it.copy(reglesEnCours = enCours, finDernieresRegles = null, erreur = null) }

    fun choisirFin(date: LocalDate) = _formulaire.update { it.copy(finDernieresRegles = date, reglesEnCours = false, erreur = null) }

    fun changerDureeCycle(jours: Int) =
        _formulaire.update { it.copy(dureeCycle = jours.coerceIn(it.plageCycle), erreur = null) }

    fun changerDureeRegles(jours: Int) =
        _formulaire.update { it.copy(dureeRegles = jours.coerceIn(it.plageRegles), erreur = null) }

    /** Choisir la contraception déjà choisie la retire : le champ est facultatif. */
    fun choisirContraception(choix: Contraception) =
        _formulaire.update { it.copy(contraception = if (it.contraception == choix) null else choix) }

    fun enregistrer() {
        val f = _formulaire.value
        if (f.enregistrement) return
        _formulaire.update { it.copy(enregistrement = true, erreur = null) }
        viewModelScope.launch {
            val configuration = ConfigurationInitiale(
                dernieresRegles = f.dernieresRegles.takeIf { f.demanderDate },
                // Règles encore en cours, ou commencées aujourd'hui : pas de fin.
                finDernieresRegles = f.finDernieresRegles.takeIf { f.demanderFin && !f.reglesEnCours },
                parametres = ParametresCycle(f.dureeCycle, f.dureeRegles, f.contraception),
            )
            val erreur = erreurDe(depot.configurer(configuration, aujourdhui()))
            _formulaire.update { it.copy(enregistrement = false, erreur = erreur, enregistre = erreur == null) }
        }
    }

    private fun aujourdhui(): LocalDate = LocalDate.now(dependances.horloge)

    companion object {
        fun fabrique(dependances: DependancesCycle): ViewModelProvider.Factory = viewModelFactory {
            initializer { ConfigurationViewModel(dependances) }
        }
    }
}
