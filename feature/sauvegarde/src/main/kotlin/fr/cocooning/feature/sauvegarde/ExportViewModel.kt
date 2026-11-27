package fr.cocooning.feature.sauvegarde

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.cocooning.core.securite.sauvegarde.PhraseRecuperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class EtapeExport {
    /** Saisie de la phrase. */
    Saisie,

    /** Chiffrement puis écriture du fichier choisi. */
    Ecriture,

    /** Fichier écrit. */
    Termine,
}

/**
 * Formulaire de création de la phrase de récupération et d'export (EF-CPT-08).
 *
 * La phrase n'est jamais enregistrée : elle ne vit que dans ce formulaire et disparaît après
 * l'export. Chaque export la redemande.
 */
data class FormulaireExport(
    val phrase: String = "",
    val confirmation: String = "",
    val afficher: Boolean = false,
    val compris: Boolean = false,
    val etape: EtapeExport = EtapeExport.Saisie,
    val dernierExport: LocalDate? = null,
    val nomFichierPropose: String = "",
    /** Ce qui manque, dit après un appui sur le bouton d'export. */
    val manques: List<String> = emptyList(),
    val erreur: String? = null,
) {
    val robustesse: PhraseRecuperation.Robustesse get() = PhraseRecuperation.evaluer(phrase)

    /** Comparées comme le moteur les compare : majuscules et espaces en trop ignorés. */
    val confirmationIdentique: Boolean
        get() = PhraseRecuperation.normaliser(phrase) == PhraseRecuperation.normaliser(confirmation)

    /** Affichée dès que la confirmation s'écarte de la phrase, pas pendant qu'elle est en cours de frappe. */
    val erreurConfirmation: String?
        get() {
            val attendue = PhraseRecuperation.normaliser(phrase)
            val tapee = PhraseRecuperation.normaliser(confirmation)
            return if (tapee.isNotEmpty() && !attendue.startsWith(tapee)) "Les deux phrases ne sont pas identiques." else null
        }

    val pret: Boolean get() = robustesse.acceptable && confirmationIdentique && compris
}

class ExportViewModel(private val dependances: DependancesSauvegarde) : ViewModel() {

    private val _formulaire = MutableStateFlow(FormulaireExport(nomFichierPropose = nomFichier(aujourdhui())))
    val formulaire: StateFlow<FormulaireExport> = _formulaire.asStateFlow()

    init {
        viewModelScope.launch {
            val dernier = dependances.suivi.dernierExport.first()
            _formulaire.update { it.copy(dernierExport = dernier) }
        }
    }

    fun changerPhrase(texte: String) = _formulaire.update { it.copy(phrase = texte, manques = emptyList(), erreur = null) }

    fun changerConfirmation(texte: String) =
        _formulaire.update { it.copy(confirmation = texte, manques = emptyList(), erreur = null) }

    fun basculerAffichage() = _formulaire.update { it.copy(afficher = !it.afficher) }

    fun indiquerCompris(compris: Boolean) = _formulaire.update { it.copy(compris = compris, manques = emptyList()) }

    /**
     * Appelé par le bouton d'export avant d'ouvrir le sélecteur de fichiers. Faux, avec la liste
     * de ce qui manque, si la phrase ne peut pas encore protéger une sauvegarde.
     */
    fun pretPourChoisirFichier(): Boolean {
        val f = _formulaire.value
        if (f.etape != EtapeExport.Saisie) return false
        val manques = buildList {
            if (!f.robustesse.acceptable) add("une phrase assez longue et difficile à deviner")
            if (!f.confirmationIdentique || f.confirmation.isBlank()) add("la même phrase dans le second champ")
            if (!f.compris) add("la case qui confirme que tu as noté ta phrase")
        }
        _formulaire.update { it.copy(manques = manques, erreur = null) }
        return manques.isEmpty()
    }

    /** Le sélecteur a rendu [adresse] (nul si l'utilisateur a annulé). */
    fun exporterVers(adresse: String?) {
        val f = _formulaire.value
        if (adresse == null || f.etape != EtapeExport.Saisie || !f.pret) return
        _formulaire.update { it.copy(etape = EtapeExport.Ecriture, erreur = null) }
        viewModelScope.launch {
            try {
                val octets = dependances.coffre.exporter(f.phrase)
                dependances.fichiers.ecrire(adresse, octets)
                val jour = aujourdhui()
                dependances.suivi.noterExport(jour)
                _formulaire.update {
                    it.copy(etape = EtapeExport.Termine, phrase = "", confirmation = "", dernierExport = jour)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _formulaire.update {
                    it.copy(
                        etape = EtapeExport.Saisie,
                        erreur = "Le fichier n'a pas pu être enregistré. Vérifie qu'il reste de la place, " +
                            "ou choisis un autre endroit, puis réessaie.",
                    )
                }
            }
        }
    }

    private fun aujourdhui(): LocalDate = LocalDate.now(dependances.horloge)

    companion object {
        /** Extension propre à Cocooning, pour reconnaître le fichier dans un dossier. */
        const val EXTENSION = "cocooning"

        /** Type annoncé au sélecteur : le fichier est opaque. */
        const val TYPE_MIME = "application/octet-stream"

        fun nomFichier(jour: LocalDate) = "cocooning-sauvegarde-$jour.$EXTENSION"

        fun fabrique(dependances: DependancesSauvegarde): ViewModelProvider.Factory = viewModelFactory {
            initializer { ExportViewModel(dependances) }
        }
    }
}
