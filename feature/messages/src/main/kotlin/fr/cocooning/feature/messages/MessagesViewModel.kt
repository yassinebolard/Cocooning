package fr.cocooning.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.cocooning.core.messagerie.domaine.ErreurMessagerie
import fr.cocooning.core.messagerie.domaine.IdMessage
import fr.cocooning.core.messagerie.domaine.Message
import fr.cocooning.core.reseau.ErreurReseau
import fr.cocooning.core.reseau.EtatInscription
import fr.cocooning.core.reseau.ServiceMessagerie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import fr.cocooning.core.messagerie.donnees.ResultatMessagerie

/** Ce que montre l'onglet Messages. */
sealed interface MessagesUiState {
    data object Chargement : MessagesUiState

    /**
     * Pas encore d'espace : créer le sien ou rejoindre avec un code.
     *
     * @param enCours une demande part vers le serveur.
     * @param erreur texte à afficher sous le formulaire.
     */
    data class Inscription(val enCours: Boolean = false, val erreur: String? = null) : MessagesUiState

    /** Une sauvegarde a été restaurée sur ce téléphone : il faut le reconnecter. */
    data class Reprise(val enCours: Boolean = false, val erreur: String? = null) : MessagesUiState

    /**
     * @param invitation code à donner au partenaire, tant qu'il n'a pas encore rejoint.
     * @param horsLigne la dernière synchronisation n'a pas pu joindre le serveur.
     * @param telephoneChange le partenaire a changé de téléphone : code de sécurité à comparer.
     */
    data class Conversation(
        val messages: List<Message> = emptyList(),
        val saisie: String = "",
        val invitation: String? = null,
        val horsLigne: Boolean = false,
        val telephoneChange: Boolean = false,
        /** Pourquoi le dernier texte n'a pas pu être enregistré ; il est rendu à la saisie. */
        val erreurEnvoi: String? = null,
    ) : MessagesUiState
}

/**
 * Onglet Messages. Les messages s'enregistrent tout de suite sur le téléphone et partent à la
 * synchronisation suivante (EF-MSG-10) : à l'écriture, à l'ouverture, puis toutes les
 * [intervalleSynchro] millisecondes tant que l'écran est ouvert.
 */
class MessagesViewModel(
    private val service: ServiceMessagerie,
    private val intervalleSynchro: Long = 20_000,
) : ViewModel() {

    private val _etat = MutableStateFlow<MessagesUiState>(MessagesUiState.Chargement)
    val etat: StateFlow<MessagesUiState> = _etat.asStateFlow()

    private var conversation: Job? = null
    private var boucle: Job? = null

    /** Messages dont l'accusé « lu » est déjà parti pendant cette ouverture de l'onglet. */
    private val accuses = mutableSetOf<IdMessage>()

    init {
        viewModelScope.launch {
            _etat.value = when (service.etat()) {
                EtatInscription.NonInscrit -> MessagesUiState.Inscription()
                EtatInscription.AReprendre -> MessagesUiState.Reprise()
                EtatInscription.Inscrit -> {
                    ouvrirConversation(null)
                    return@launch
                }
            }
        }
    }

    fun creerEspace() = inscrire { service.creerEspace().first }

    fun rejoindre(code: String) {
        if (code.isBlank()) {
            _etat.value = MessagesUiState.Inscription(erreur = "Saisis le code reçu de ton partenaire.")
            return
        }
        inscrire {
            service.rejoindre(code)
            null
        }
    }

    fun reprendre() {
        _etat.value = MessagesUiState.Reprise(enCours = true)
        viewModelScope.launch {
            try {
                service.reprendre()
                ouvrirConversation(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _etat.value = MessagesUiState.Reprise(erreur = texteErreur(e))
            }
        }
    }

    fun modifierSaisie(texte: String) = majConversation { it.copy(saisie = texte) }

    fun envoyer() {
        val actuel = _etat.value as? MessagesUiState.Conversation ?: return
        val texte = actuel.saisie
        if (texte.isBlank()) return
        majConversation { it.copy(saisie = "", erreurEnvoi = null) }
        viewModelScope.launch {
            val resultat = service.ecrire(texte)
            if (resultat !is ResultatMessagerie.Fait) {
                // Rien n'est enregistré : on rend le texte pour ne pas le perdre.
                val raison = when ((resultat as? ResultatMessagerie.Refuse)?.erreur) {
                    ErreurMessagerie.AucunDestinataire -> "Ton partenaire n'a pas encore rejoint l'espace : donne-lui le code d'abord."
                    is ErreurMessagerie.TexteTropLong -> "Ce message est trop long."
                    else -> "Ce message n'a pas pu être enregistré. Réessaie."
                }
                majConversation { it.copy(saisie = texte, erreurEnvoi = raison) }
            }
            synchroniser()
        }
    }

    fun reessayer(id: IdMessage) {
        viewModelScope.launch {
            service.reessayer(id)
            synchroniser()
        }
    }

    /** Les messages reçus affichés à l'écran sont « lus » (EF-MSG-07). */
    fun messagesAffiches(messages: List<Message>) {
        val nonLus = messages.filter { !it.sortant && !it.supprimePourTous && it.id !in accuses }
        if (nonLus.isEmpty()) return
        accuses += nonLus.map { it.id }
        viewModelScope.launch {
            // Hors ligne : on les oublie pour réessayer au prochain affichage.
            runCatching { service.accuserLecture(nonLus) }.onFailure { accuses -= nonLus.map { m -> m.id }.toSet() }
        }
    }

    fun ignorerChangementTelephone() = majConversation { it.copy(telephoneChange = false) }

    fun synchroniserMaintenant() {
        viewModelScope.launch { synchroniser() }
    }

    private suspend fun synchroniser() {
        try {
            val rapport = service.synchroniser()
            majConversation { it.copy(horsLigne = false, telephoneChange = it.telephoneChange || rapport.identitesChangees.isNotEmpty()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ErreurReseau) {
            majConversation { it.copy(horsLigne = true) }
        }
    }

    private fun inscrire(action: suspend () -> String?) {
        _etat.value = MessagesUiState.Inscription(enCours = true)
        viewModelScope.launch {
            try {
                ouvrirConversation(action())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _etat.value = MessagesUiState.Inscription(erreur = texteErreur(e))
            }
        }
    }

    private fun ouvrirConversation(invitation: String?) {
        _etat.value = MessagesUiState.Conversation(invitation = invitation)
        conversation?.cancel()
        conversation = viewModelScope.launch {
            service.conversation().collect { messages ->
                majConversation {
                    // Le code n'a plus d'utilité dès que le partenaire a écrit.
                    it.copy(messages = messages, invitation = it.invitation.takeIf { _ -> messages.none { m -> !m.sortant } })
                }
            }
        }
        boucle?.cancel()
        boucle = viewModelScope.launch {
            while (isActive) {
                synchroniser()
                delay(intervalleSynchro)
            }
        }
    }

    private fun majConversation(transformation: (MessagesUiState.Conversation) -> MessagesUiState.Conversation) =
        _etat.update { if (it is MessagesUiState.Conversation) transformation(it) else it }

    private fun texteErreur(e: Exception): String = when {
        e is ErreurReseau.HorsLigne -> "Le serveur Cocooning est injoignable. Vérifie ta connexion et réessaie."
        e is ErreurReseau.Http && e.erreur?.code == "invitation_invalide" -> "Ce code est inconnu ou a expiré. Demande un nouveau code."
        e is ErreurReseau.Http && e.erreur?.code == "espace_complet" -> "Cet espace a déjà ses deux membres."
        e is ErreurReseau.Http && e.erreur?.code == "recuperation_refusee" ->
            "Cette sauvegarde ne permet plus de reconnecter le téléphone. Une sauvegarde plus récente a peut-être été restaurée ailleurs."
        else -> "Quelque chose n'a pas marché. Réessaie dans un instant."
    }

    companion object {
        fun fabrique(service: ServiceMessagerie): ViewModelProvider.Factory = viewModelFactory {
            initializer { MessagesViewModel(service) }
        }
    }
}
