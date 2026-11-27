package fr.cocooning.feature.sauvegarde

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.cocooning.core.securite.sauvegarde.ContenuSauvegarde
import fr.cocooning.core.securite.sauvegarde.EchecOuverture
import fr.cocooning.core.securite.sauvegarde.SauvegardeChiffree
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EtapeRestauration {
    ChoixFichier,
    LectureFichier,
    Saisie,
    Verification,
    Restauration,
    Termine,
    ;

    /** Pendant ces étapes, rien ne doit interrompre le travail en cours. */
    val occupe: Boolean get() = this == LectureFichier || this == Verification || this == Restauration
}

/**
 * Restauration au premier lancement (EF-CPT-08) : choisir le fichier, taper la phrase, vérifier,
 * remettre en place, redémarrer.
 *
 * @param erreurFichier problème du fichier choisi (pas une sauvegarde, abîmée, trop récente).
 * @param erreurPhrase la phrase n'ouvre pas le fichier.
 * @param erreur échec inattendu de la remise en place.
 */
data class FormulaireRestauration(
    val etape: EtapeRestauration = EtapeRestauration.ChoixFichier,
    val nomFichier: String? = null,
    val phrase: String = "",
    val afficher: Boolean = false,
    val erreurFichier: String? = null,
    val erreurPhrase: String? = null,
    val erreur: String? = null,
)

class RestaurationViewModel(private val dependances: DependancesSauvegarde) : ViewModel() {

    private val _formulaire = MutableStateFlow(FormulaireRestauration())
    val formulaire: StateFlow<FormulaireRestauration> = _formulaire.asStateFlow()

    /** Contenu du fichier choisi, gardé hors de l'état affiché. */
    private var fichier: ByteArray? = null

    /** Le sélecteur a rendu [adresse] (nul si l'utilisateur a annulé). */
    fun fichierChoisi(adresse: String?) {
        if (adresse == null || _formulaire.value.etape.occupe) return
        _formulaire.update { it.copy(etape = EtapeRestauration.LectureFichier, erreurFichier = null, erreurPhrase = null, erreur = null) }
        viewModelScope.launch {
            val resultat = try {
                Result.success(dependances.fichiers.lire(adresse))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            val octets = resultat.getOrNull()
            val probleme = when {
                resultat.exceptionOrNull() is FichierTropGros -> PAS_UNE_SAUVEGARDE
                octets == null -> "Ce fichier n'a pas pu être lu. Vérifie qu'il est bien sur ce téléphone, " +
                    "ou choisis-le à nouveau."
                !SauvegardeChiffree.estUneSauvegarde(octets) -> PAS_UNE_SAUVEGARDE
                else -> null
            }
            if (probleme != null) {
                fichier = null
                _formulaire.update { it.copy(etape = EtapeRestauration.ChoixFichier, nomFichier = null, erreurFichier = probleme) }
                return@launch
            }
            fichier = octets
            val nom = dependances.fichiers.nom(adresse)
            _formulaire.update { it.copy(etape = EtapeRestauration.Saisie, nomFichier = nom) }
        }
    }

    fun changerPhrase(texte: String) = _formulaire.update { it.copy(phrase = texte, erreurPhrase = null, erreur = null) }

    fun basculerAffichage() = _formulaire.update { it.copy(afficher = !it.afficher) }

    fun restaurer() {
        val f = _formulaire.value
        val octets = fichier
        if (f.etape != EtapeRestauration.Saisie || octets == null) return
        if (f.phrase.isBlank()) {
            _formulaire.update { it.copy(erreurPhrase = "Tape la phrase de récupération choisie lors de la sauvegarde.") }
            return
        }
        _formulaire.update { it.copy(etape = EtapeRestauration.Verification, erreurPhrase = null, erreur = null) }
        viewModelScope.launch {
            val contenu: ContenuSauvegarde = try {
                dependances.coffre.ouvrir(octets, f.phrase)
            } catch (e: EchecOuverture.PhraseIncorrecte) {
                _formulaire.update { it.copy(etape = EtapeRestauration.Saisie, erreurPhrase = PHRASE_INCORRECTE) }
                return@launch
            } catch (e: EchecOuverture.FichierIllisible) {
                fichier = null
                _formulaire.update {
                    it.copy(
                        etape = EtapeRestauration.ChoixFichier,
                        nomFichier = null,
                        erreurFichier = messageFichierIllisible(e),
                    )
                }
                return@launch
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _formulaire.update { it.copy(etape = EtapeRestauration.Saisie, erreur = ECHEC_RESTAURATION) }
                return@launch
            }

            _formulaire.update { it.copy(etape = EtapeRestauration.Restauration) }
            try {
                dependances.coffre.restaurer(contenu)
                dependances.suivi.terminerPremierLancement()
                fichier = null
                _formulaire.update { it.copy(etape = EtapeRestauration.Termine, phrase = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _formulaire.update { it.copy(etape = EtapeRestauration.Saisie, erreur = ECHEC_RESTAURATION) }
            } finally {
                contenu.entrees.values.forEach { it.fill(0) }
            }
        }
    }

    /** Revient au choix du fichier, par exemple pour en prendre un plus récent. */
    fun changerDeFichier() {
        if (_formulaire.value.etape.occupe) return
        fichier = null
        _formulaire.update { FormulaireRestauration(afficher = it.afficher) }
    }

    override fun onCleared() {
        fichier?.fill(0)
        fichier = null
    }

    companion object {
        internal const val PAS_UNE_SAUVEGARDE =
            "Ce fichier n'est pas une sauvegarde Cocooning. Cherche un fichier qui finit par " +
                "« .${ExportViewModel.EXTENSION} »."

        internal const val PHRASE_INCORRECTE =
            "Cette phrase n'ouvre pas ce fichier. Vérifie chaque mot : les majuscules et les espaces " +
                "en trop ne comptent pas, mais les accents et la ponctuation, si."

        internal const val ECHEC_RESTAURATION =
            "La restauration n'a pas abouti. Réessaie ; si cela recommence, tu peux commencer sans " +
                "sauvegarde et réessayer plus tard."

        internal fun messageFichierIllisible(e: EchecOuverture.FichierIllisible): String =
            if (e.message.orEmpty().contains("plus récente")) {
                "Cette sauvegarde vient d'une version plus récente de Cocooning. Mets l'appli à jour, puis réessaie."
            } else {
                "Ce fichier est abîmé ou incomplet. Choisis une autre copie de ta sauvegarde."
            }

        fun fabrique(dependances: DependancesSauvegarde): ViewModelProvider.Factory = viewModelFactory {
            initializer { RestaurationViewModel(dependances) }
        }
    }
}
