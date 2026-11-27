package fr.cocooning.feature.sauvegarde

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.components.CocoButton
import fr.cocooning.designsystem.components.CocoButtonStyle
import fr.cocooning.designsystem.components.CocoCard
import fr.cocooning.designsystem.icons.CocoIcons
import java.time.LocalDate

/** Carte de rappel d'export, sur l'accueil. Ne dessine rien quand le rappel n'est pas dû. */
@Composable
fun RappelSauvegardeRoute(viewModel: RappelViewModel, onSauvegarder: () -> Unit, modifier: Modifier = Modifier) {
    val etat by viewModel.etat.collectAsStateWithLifecycle()
    if (etat.visible) {
        CarteRappelSauvegarde(
            dernierExport = etat.dernierExport,
            onSauvegarder = onSauvegarder,
            onPlusTard = viewModel::reporter,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarteRappelSauvegarde(
    dernierExport: LocalDate?,
    onSauvegarder: () -> Unit,
    onPlusTard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CocoCard(titre = "Sauvegarde de tes données", icone = CocoIcons.Cadenas, modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (dernierExport == null) {
                "Aucune sauvegarde pour l'instant. Si tu perds ce téléphone, ton historique serait perdu avec lui."
            } else {
                "Ta dernière sauvegarde date du ${dateLongue(dernierExport)}. Ce que tu as saisi depuis n'y est pas."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space2),
        ) {
            CocoButton(
                texte = "Sauvegarder",
                onClick = onSauvegarder,
                style = CocoButtonStyle.Tonal,
                compact = true,
            )
            CocoButton(texte = "Plus tard", onClick = onPlusTard, style = CocoButtonStyle.Texte)
        }
    }
}

/** Accès permanent à la sauvegarde, en bas de l'accueil, en attendant un écran de réglages. */
@Composable
fun LienSauvegarde(onSauvegarder: () -> Unit, modifier: Modifier = Modifier) {
    CocoButton(
        texte = "Sauvegarder mes données",
        onClick = onSauvegarder,
        style = CocoButtonStyle.Texte,
        icone = CocoIcons.Cadenas,
        modifier = modifier,
    )
}

@CocoPreviews
@Composable
private fun CarteRappelSauvegardePreview() {
    CocooningTheme {
        CarteRappelSauvegarde(dernierExport = LocalDate.of(2026, 10, 1), onSauvegarder = {}, onPlusTard = {})
    }
}
