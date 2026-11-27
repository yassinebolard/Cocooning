package fr.cocooning.feature.cycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cocooning.core.cycle.domaine.CycleSaisi
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.components.CocoButton
import fr.cocooning.designsystem.components.CocoButtonStyle
import fr.cocooning.designsystem.components.CocoCard
import fr.cocooning.designsystem.components.EmptyState
import fr.cocooning.designsystem.components.LoadingState
import fr.cocooning.designsystem.icons.CocoIcons
import java.time.LocalDate

/**
 * Correction des dates de règles passées, ou ajout de règles oubliées. Une fois enregistré,
 * l'écran se referme et l'onglet Cycle montre les durées et moyennes recalculées.
 *
 * @param onTermine reçoit la phrase à annoncer, puis l'appelant referme l'écran.
 */
@Composable
fun CorrectionRoute(
    viewModel: CorrectionViewModel,
    onTermine: (String) -> Unit,
    onRetour: () -> Unit,
    modifier: Modifier = Modifier,
    margeBas: PaddingValues = PaddingValues(),
) {
    val formulaire by viewModel.formulaire.collectAsStateWithLifecycle()
    LaunchedEffect(formulaire.termine) { formulaire.termine?.let(onTermine) }
    EcranCorrection(
        formulaire = formulaire,
        actions = ActionsCorrection(
            onDebut = viewModel::choisirDebut,
            onFin = viewModel::choisirFin,
            onEffacerFin = viewModel::effacerFin,
            onEnregistrer = viewModel::enregistrer,
            onSupprimer = viewModel::supprimer,
            onRetour = onRetour,
        ),
        modifier = modifier,
        margeBas = margeBas,
    )
}

data class ActionsCorrection(
    val onDebut: (LocalDate) -> Unit = {},
    val onFin: (LocalDate) -> Unit = {},
    val onEffacerFin: () -> Unit = {},
    val onEnregistrer: () -> Unit = {},
    val onSupprimer: () -> Unit = {},
    val onRetour: () -> Unit = {},
)

@Composable
fun EcranCorrection(
    formulaire: FormulaireCorrection,
    actions: ActionsCorrection,
    modifier: Modifier = Modifier,
    margeBas: PaddingValues = PaddingValues(),
) {
    val f = formulaire
    when {
        !f.charge -> LoadingState(modifier.padding(Dimens.Space5))
        f.introuvable -> EmptyState(
            titre = "Règles introuvables",
            message = "Elles ont été supprimées entre-temps.",
            icone = CocoIcons.Goutte,
            modifier = modifier.fillMaxSize(),
            action = { CocoButton(texte = "Revenir au cycle", onClick = actions.onRetour) },
        )
        else -> FormulaireCorrectionContenu(f, actions, modifier, margeBas)
    }
}

@Composable
private fun FormulaireCorrectionContenu(
    f: FormulaireCorrection,
    actions: ActionsCorrection,
    modifier: Modifier,
    margeBas: PaddingValues,
) {
    var confirmerSuppression by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(margeBas)
            .padding(horizontal = Dimens.Space5, vertical = Dimens.Space6),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space4),
    ) {
        EnTeteCycle(
            titre = if (f.ajout) "Ajouter des règles" else "Corriger les dates",
            sousTitre = if (f.ajout) {
                "Pour des règles passées que tu n'as pas indiquées le jour même."
            } else {
                "Tes durées et tes moyennes seront recalculées."
            },
            onRetour = actions.onRetour,
        )

        ChampDate(
            libelle = "Premier jour des règles",
            date = f.debut,
            aujourdhui = f.aujourdhui,
            onChoisir = actions.onDebut,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space1)) {
            ChampDate(
                libelle = "Dernier jour des règles",
                date = f.fin,
                aujourdhui = f.aujourdhui,
                onChoisir = actions.onFin,
                texteVide = "Pas encore indiqué",
                aide = if (f.fin == null) "Facultatif. Sans date, la durée sera estimée." else null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (f.fin != null) {
                CocoButton(
                    texte = "Effacer le dernier jour",
                    onClick = actions.onEffacerFin,
                    style = CocoButtonStyle.Texte,
                )
            }
        }

        f.dureeRegles?.let { duree ->
            CocoCard(tonale = true, modifier = Modifier.fillMaxWidth()) {
                Text("Règles de ${jours(duree)}", style = MaterialTheme.typography.bodyLarge)
            }
        }

        f.erreur?.let { TexteSousChamp(it, erreur = true) }

        CocoButton(
            texte = if (f.ajout) "Ajouter ces règles" else "Enregistrer la correction",
            onClick = actions.onEnregistrer,
            enabled = f.modifie && f.debut != null,
            chargement = f.enregistrement,
            texteChargement = "Enregistrement…",
            modifier = Modifier.fillMaxWidth(),
        )
        if (!f.ajout) {
            CocoButton(
                texte = "Supprimer ces règles",
                onClick = { confirmerSuppression = true },
                style = CocoButtonStyle.Texte,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmerSuppression) {
        AlertDialog(
            onDismissRequest = { confirmerSuppression = false },
            title = { Text("Supprimer ces règles ?") },
            text = {
                Text("À faire seulement si elles ont été saisies par erreur. Tes moyennes seront recalculées sans elles.")
            },
            confirmButton = {
                CocoButton(
                    texte = "Supprimer",
                    style = CocoButtonStyle.Destructif,
                    compact = true,
                    onClick = {
                        confirmerSuppression = false
                        actions.onSupprimer()
                    },
                )
            },
            dismissButton = {
                CocoButton(texte = "Garder", style = CocoButtonStyle.Texte, onClick = { confirmerSuppression = false })
            },
        )
    }
}

@CocoPreviews
@Composable
private fun EcranCorrectionPreview() {
    val aujourdhui = LocalDate.of(2026, 11, 27)
    val cycle = CycleSaisi(3, LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 31))
    CocooningTheme {
        EcranCorrection(
            formulaire = FormulaireCorrection(
                charge = true,
                aujourdhui = aujourdhui,
                origine = cycle,
                debut = cycle.debut.minusDays(1),
                fin = cycle.finRegles,
            ),
            actions = ActionsCorrection(),
        )
    }
}
