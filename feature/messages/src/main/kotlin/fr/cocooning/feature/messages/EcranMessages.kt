package fr.cocooning.feature.messages

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cocooning.core.messagerie.domaine.ContenuMessage
import fr.cocooning.core.messagerie.domaine.Message
import fr.cocooning.core.messagerie.domaine.ReglesMessages
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.components.CocoButton
import fr.cocooning.designsystem.components.CocoButtonStyle
import fr.cocooning.designsystem.components.CocoCard
import fr.cocooning.designsystem.components.CocoTextField
import fr.cocooning.designsystem.components.DirectionMessage
import fr.cocooning.designsystem.components.EmptyState
import fr.cocooning.designsystem.components.LoadingState
import fr.cocooning.designsystem.components.MessageBubble
import fr.cocooning.designsystem.components.MessageComposer
import fr.cocooning.designsystem.icons.CocoIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import fr.cocooning.core.messagerie.domaine.StatutMessage as StatutDomaine
import fr.cocooning.designsystem.components.StatutMessage as StatutBulle

/**
 * Onglet Messages branché à son ViewModel.
 *
 * @param prenomPartenaire prénom affiché et lu par TalkBack pour les messages reçus.
 * @param onAnnoncer message éphémère (pièces jointes et vocal à venir).
 * @param margeBas place laissée sous le contenu pour le bouton S.O.S flottant.
 */
@Composable
fun MessagesRoute(
    viewModel: MessagesViewModel,
    prenomPartenaire: String,
    onAnnoncer: (String) -> Unit,
    modifier: Modifier = Modifier,
    margeBas: PaddingValues = PaddingValues(),
) {
    val etat by viewModel.etat.collectAsStateWithLifecycle()
    EcranSecurise()
    EcranMessages(
        etat = etat,
        prenomPartenaire = prenomPartenaire,
        actions = ActionsMessages(
            onCreerEspace = viewModel::creerEspace,
            onRejoindre = viewModel::rejoindre,
            onReprendre = viewModel::reprendre,
            onSaisie = viewModel::modifierSaisie,
            onEnvoyer = viewModel::envoyer,
            onReessayer = { viewModel.reessayer(it.id) },
            onAffiches = viewModel::messagesAffiches,
            onFermerAlerte = viewModel::ignorerChangementTelephone,
            onBientot = onAnnoncer,
        ),
        modifier = modifier,
        margeBas = margeBas,
    )
}

/** Ce que l'écran peut demander. */
data class ActionsMessages(
    val onCreerEspace: () -> Unit = {},
    val onRejoindre: (String) -> Unit = {},
    val onReprendre: () -> Unit = {},
    val onSaisie: (String) -> Unit = {},
    val onEnvoyer: () -> Unit = {},
    val onReessayer: (Message) -> Unit = {},
    val onAffiches: (List<Message>) -> Unit = {},
    val onFermerAlerte: () -> Unit = {},
    val onBientot: (String) -> Unit = {},
)

/** Onglet Messages sans état. */
@Composable
fun EcranMessages(
    etat: MessagesUiState,
    prenomPartenaire: String,
    actions: ActionsMessages,
    modifier: Modifier = Modifier,
    margeBas: PaddingValues = PaddingValues(),
) {
    when (etat) {
        MessagesUiState.Chargement -> LoadingState(modifier.padding(Dimens.Space5))
        is MessagesUiState.Inscription -> Inscription(etat, prenomPartenaire, actions, modifier, margeBas)
        is MessagesUiState.Reprise -> Reprise(etat, actions, modifier, margeBas)
        is MessagesUiState.Conversation -> Conversation(etat, prenomPartenaire, actions, modifier, margeBas)
    }
}

@Composable
private fun Formulaire(modifier: Modifier, margeBas: PaddingValues, contenu: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(margeBas)
            .padding(horizontal = Dimens.Space5, vertical = Dimens.Space6),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space4),
    ) { contenu() }
}

@Composable
private fun Titre(texte: String) {
    Text(texte, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Inscription(
    etat: MessagesUiState.Inscription,
    prenomPartenaire: String,
    actions: ActionsMessages,
    modifier: Modifier,
    margeBas: PaddingValues,
) {
    var code by rememberSaveable { mutableStateOf("") }
    Formulaire(modifier, margeBas) {
        Titre("Votre messagerie à deux")
        Text(
            "Vos messages sont chiffrés sur ton téléphone avant de partir : même le serveur Cocooning ne peut pas les lire.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CocoCard(titre = "Je commence", icone = CocoIcons.Cadenas, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Crée votre espace. Tu recevras un code à donner à $prenomPartenaire, valable 24 heures.",
                style = MaterialTheme.typography.bodyMedium,
            )
            CocoButton(
                texte = "Créer notre espace",
                onClick = actions.onCreerEspace,
                enabled = !etat.enCours,
                chargement = etat.enCours,
                texteChargement = "Création…",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        CocoCard(titre = "J'ai reçu un code", icone = CocoIcons.Bulle, modifier = Modifier.fillMaxWidth()) {
            CocoTextField(
                valeur = code,
                onValeurChange = { code = it.uppercase().take(12) },
                libelle = "Code d'invitation",
                aide = "8 lettres et chiffres, sans 0 ni O",
                erreur = etat.erreur,
                enabled = !etat.enCours,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
            )
            CocoButton(
                texte = "Rejoindre l'espace",
                onClick = { actions.onRejoindre(code) },
                style = CocoButtonStyle.Tonal,
                enabled = !etat.enCours,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Reprise(etat: MessagesUiState.Reprise, actions: ActionsMessages, modifier: Modifier, margeBas: PaddingValues) {
    Formulaire(modifier, margeBas) {
        Titre("Reconnecter ce téléphone")
        Text(
            "Ta sauvegarde est bien restaurée. Pour recevoir de nouveau tes messages, ce téléphone doit prendre la place " +
                "de l'ancien : l'ancien sera déconnecté, et ton partenaire verra que ton code de sécurité a changé.",
            style = MaterialTheme.typography.bodyMedium,
        )
        etat.erreur?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        CocoButton(
            texte = "Reconnecter ce téléphone",
            onClick = actions.onReprendre,
            enabled = !etat.enCours,
            chargement = etat.enCours,
            texteChargement = "Connexion…",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Conversation(
    etat: MessagesUiState.Conversation,
    prenomPartenaire: String,
    actions: ActionsMessages,
    modifier: Modifier,
    margeBas: PaddingValues,
) {
    val liste = rememberLazyListState()
    LaunchedEffect(etat.messages.size) {
        if (etat.messages.isNotEmpty()) liste.animateScrollToItem(etat.messages.lastIndex)
        actions.onAffiches(etat.messages)
    }
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier.padding(horizontal = Dimens.Space4, vertical = Dimens.Space2),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space2),
        ) {
            etat.invitation?.let { code ->
                CocoCard(titre = "Code pour $prenomPartenaire", icone = CocoIcons.Cadenas, tonale = true, modifier = Modifier.fillMaxWidth()) {
                    Text(code.chunked(4).joinToString(" "), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Donne-lui ce code de vive voix : il est valable 24 heures et ne sert qu'une fois.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (etat.telephoneChange) {
                CocoCard(titre = "Nouveau téléphone", icone = CocoIcons.Alerte, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "$prenomPartenaire a changé de téléphone : votre code de sécurité a changé. " +
                            "Si ce n'est pas le cas, parlez-en avant d'écrire quelque chose de sensible.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    CocoButton(texte = "C'est bien son téléphone", onClick = actions.onFermerAlerte, style = CocoButtonStyle.Texte, compact = true)
                }
            }
            etat.erreurEnvoi?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (etat.horsLigne) {
                Text(
                    "Hors ligne : tes messages partiront dès le retour du réseau.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (etat.messages.isEmpty()) {
            EmptyState(
                titre = "Pas encore de message",
                message = "Écris le premier à $prenomPartenaire.",
                icone = CocoIcons.Bulle,
                modifier = Modifier.weight(1f).padding(Dimens.Space5),
            )
        } else {
            LazyColumn(
                state = liste,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Dimens.Space4, vertical = Dimens.Space2),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space2),
            ) {
                items(etat.messages, key = { it.id.valeur }) { message ->
                    Bulle(message, prenomPartenaire, onReessayer = { actions.onReessayer(message) })
                }
            }
        }
        MessageComposer(
            valeur = etat.saisie,
            onValeurChange = actions.onSaisie,
            onEnvoyer = actions.onEnvoyer,
            onJoindre = { actions.onBientot("Les photos, GIF et vidéos arrivent bientôt.") },
            onMicro = { actions.onBientot("Les notes vocales arrivent bientôt.") },
            modifier = Modifier.padding(margeBas),
        )
    }
}

private val formatHeure = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun Bulle(message: Message, prenomPartenaire: String, onReessayer: () -> Unit) {
    val heure = ReglesMessages.heureAffichee(message).atZone(ZoneId.systemDefault())
    val statut = ReglesMessages.statut(message)
    val texte = when {
        message.supprimePourTous -> "Message supprimé"
        else -> (message.contenu as? ContenuMessage.Texte)?.corps.orEmpty()
    }
    MessageBubble(
        texte = texte,
        direction = if (message.sortant) DirectionMessage.Sortant else DirectionMessage.Entrant,
        auteur = if (message.sortant) "Toi" else prenomPartenaire,
        heure = if (statut == StatutDomaine.EnAttente) "envoi…" else formatHeure.format(heure),
        heureParlee = if (statut == StatutDomaine.EnAttente) "en attente d'envoi" else "${heure.hour} h ${"%02d".format(heure.minute)}",
        statut = when (statut) {
            StatutDomaine.EnAttente -> null
            StatutDomaine.Echec -> StatutBulle.Echec
            StatutDomaine.Envoye -> StatutBulle.Envoye
            StatutDomaine.Recu -> StatutBulle.Distribue
            StatutDomaine.Lu -> StatutBulle.Lu
        },
        onReessayer = onReessayer,
    )
}

/** Pas de capture d'écran ni d'aperçu dans les applis récentes sur la messagerie (ENF-SEC-08). */
@Composable
private fun EcranSecurise() {
    val vue = LocalView.current
    DisposableEffect(vue) {
        var contexte = vue.context
        while (contexte is ContextWrapper && contexte !is Activity) contexte = contexte.baseContext
        val fenetre = (contexte as? Activity)?.window
        fenetre?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { fenetre?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

@CocoPreviews
@Composable
private fun EcranMessagesPreview() {
    val t = Instant.parse("2026-11-28T18:02:00Z")
    val messages = listOf(
        apercu("1", false, "Tu rentres à quelle heure ce soir ?", t),
        apercu("2", true, "Vers 19 h, je passe prendre le pain", t.plusSeconds(60)),
    )
    CocooningTheme {
        EcranMessages(MessagesUiState.Conversation(messages = messages), "Mymy", ActionsMessages())
    }
}

@CocoPreviews
@Composable
private fun InscriptionPreview() {
    CocooningTheme { EcranMessages(MessagesUiState.Inscription(), "Mymy", ActionsMessages()) }
}

private fun apercu(id: String, sortant: Boolean, texte: String, le: Instant) = Message(
    id = fr.cocooning.core.messagerie.domaine.IdMessage(id),
    conversation = fr.cocooning.core.messagerie.domaine.IdConversation("c"),
    auteur = fr.cocooning.core.messagerie.domaine.IdMembre(if (sortant) "moi" else "elle"),
    sortant = sortant,
    contenu = ContenuMessage.Texte(texte),
    reponseA = null,
    creeLe = le,
    arriveLe = le,
    etatEnvoi = fr.cocooning.core.messagerie.domaine.EtatEnvoi.Envoye,
    accuses = if (sortant) listOf(fr.cocooning.core.messagerie.domaine.Accuse(fr.cocooning.core.messagerie.domaine.IdMembre("elle"), le, le)) else emptyList(),
)
