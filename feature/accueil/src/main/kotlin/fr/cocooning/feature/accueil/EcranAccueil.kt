package fr.cocooning.feature.accueil

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.components.CocoButton
import fr.cocooning.designsystem.components.CocoCard
import fr.cocooning.designsystem.components.ErrorState
import fr.cocooning.designsystem.components.LoadingState
import fr.cocooning.designsystem.components.PhaseCard
import fr.cocooning.designsystem.icons.CocoIcons
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Où mène chaque bloc de l'accueil. L'application les relie aux onglets. */
data class ActionsAccueil(
    val onOuvrirMessages: () -> Unit = {},
    val onOuvrirCycle: () -> Unit = {},
    val onOuvrirCalendrier: () -> Unit = {},
    val onOuvrirNotes: () -> Unit = {},
    val onAnnoncer: (String) -> Unit = {},
)

/**
 * Accueil branché à son [AccueilViewModel].
 *
 * @param margeBas place laissée sous le contenu pour le bouton S.O.S flottant.
 */
@Composable
fun AccueilRoute(
    viewModel: AccueilViewModel,
    actions: ActionsAccueil,
    modifier: Modifier = Modifier,
    margeBas: PaddingValues = PaddingValues(),
) {
    val etat by viewModel.etat.collectAsStateWithLifecycle()
    EcranAccueil(
        etat = etat,
        actions = actions,
        onBasculerRegles = { viewModel.basculerRegles(actions.onAnnoncer) },
        modifier = modifier,
        margeBas = margeBas,
    )
}

/** Accueil sans état : tout ce qu'il affiche vient de [etat]. */
@Composable
fun EcranAccueil(
    etat: AccueilUiState,
    actions: ActionsAccueil,
    onBasculerRegles: () -> Unit,
    modifier: Modifier = Modifier,
    margeBas: PaddingValues = PaddingValues(),
) {
    when (etat) {
        AccueilUiState.Chargement -> LoadingState(modifier.padding(Dimens.Space5))
        is AccueilUiState.Erreur -> ErrorState(message = etat.message, modifier = modifier.padding(Dimens.Space5))
        is AccueilUiState.Pret -> ContenuAccueil(etat.donnees, actions, onBasculerRegles, modifier, margeBas)
    }
}

@Composable
private fun ContenuAccueil(
    donnees: DonneesAccueil,
    actions: ActionsAccueil,
    onBasculerRegles: () -> Unit,
    modifier: Modifier,
    margeBas: PaddingValues,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(margeBas)
            .padding(horizontal = Dimens.Space5, vertical = Dimens.Space6),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space3),
    ) {
        Column(Modifier.padding(bottom = Dimens.Space3)) {
            Text(
                text = "Bonjour ${donnees.prenom}",
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = jourComplet(donnees.aujourdhui),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }

        donnees.cycle?.let { cycle ->
            BlocCycle(cycle, onOuvrirCycle = actions.onOuvrirCycle, onBasculerRegles = onBasculerRegles)
        }

        donnees.dernierMessage?.let { message ->
            CocoCard(
                titre = "Messages",
                icone = CocoIcons.Bulle,
                onClick = actions.onOuvrirMessages,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "${message.auteur}, ${heure(message.heure)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = message.texte,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Dimens.Space1),
                )
                if (message.nonLus > 0) {
                    Text(
                        text = if (message.nonLus == 1) "1 message non lu" else "${message.nonLus} messages non lus",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                        modifier = Modifier.padding(top = Dimens.Space2),
                    )
                }
            }
        }

        donnees.prochainEvenement?.let { evenement ->
            CocoCard(
                titre = "Calendrier",
                icone = CocoIcons.Agenda,
                onClick = actions.onOuvrirCalendrier,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = buildString {
                        append(jourRelatif(evenement.jour, donnees.aujourdhui))
                        evenement.heure?.let { append(", ").append(heure(it)) }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = evenement.titre,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = Dimens.Space1),
                )
                evenement.lieu?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
        }

        if (donnees.notesEpinglees.isNotEmpty()) {
            NotesEpinglees(donnees.notesEpinglees, onOuvrirNotes = actions.onOuvrirNotes)
        }
    }
}

@Composable
private fun BlocCycle(cycle: ResumeCycle, onOuvrirCycle: () -> Unit, onBasculerRegles: () -> Unit) {
    when (cycle) {
        ResumeCycle.PremiereSaisie -> CocoCard(
            titre = "Suivi du cycle",
            icone = CocoIcons.Goutte,
            onClick = onOuvrirCycle,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = textePremiereSaisie(), style = MaterialTheme.typography.bodyMedium)
        }
        is ResumeCycle.Suivi -> PhaseCard(
            phase = cycle.phase,
            titre = "${cycle.phase.nom}, jour ${cycle.jour}",
            onClick = onOuvrirCycle,
        ) {
            Text(text = texteCycle(cycle), style = MaterialTheme.typography.bodyMedium)
        }
    }
    val enCours = (cycle as? ResumeCycle.Suivi)?.reglesEnCours == true
    // Seule action principale de l'écran (EF-CYC-01 : en un geste depuis l'accueil).
    CocoButton(
        texte = if (enCours) "Mes règles sont finies" else "Mes règles ont commencé",
        onClick = onBasculerRegles,
        icone = CocoIcons.Goutte,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Cartes défilant horizontalement ; à 200 % elles s'allongent au lieu de couper leur texte. */
@Composable
private fun NotesEpinglees(notes: List<ApercuNote>, onOuvrirNotes: () -> Unit) {
    Text(
        text = "Notes épinglées",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .padding(top = Dimens.Space3)
            .semantics { heading() },
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space3),
    ) {
        notes.forEach { note ->
            CocoCard(
                titre = note.titre,
                icone = CocoIcons.Note,
                tonale = true,
                onClick = onOuvrirNotes,
                modifier = Modifier.width(LargeurNote),
            ) {
                Text(text = note.extrait, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private val LargeurNote = 240.dp

@CocoPreviews
@Composable
private fun EcranAccueilPreview() {
    val donnees = runBlocking { AccueilRepositoryFictif(latenceMs = 0).donnees().first() }
    CocooningTheme {
        EcranAccueil(etat = AccueilUiState.Pret(donnees), actions = ActionsAccueil(), onBasculerRegles = {})
    }
}
