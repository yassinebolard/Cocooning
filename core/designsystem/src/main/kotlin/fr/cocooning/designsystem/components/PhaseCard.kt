package fr.cocooning.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens

/**
 * Carte de la phase du jour : fond de la couleur de phase, icône dans une pastille, titre et
 * contenu en `sur-phase`. Le titre doit nommer la phase (« Inès · phase lutéale ») : la couleur
 * ne porte jamais seule l'information.
 *
 * La hauteur suit le contenu. Si [onClick] est fourni, toute la carte est cible.
 * Dans [contenu], garder la couleur de texte héritée.
 */
@Composable
fun PhaseCard(
    phase: PhaseCycle,
    titre: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contenu: @Composable ColumnScope.() -> Unit = {},
) {
    val cocoon = CocooningTheme.cocoon
    val fond = phase.couleur(cocoon)
    val forme = MaterialTheme.shapes.large
    val bordure = BorderStroke(Dimens.Filet, MaterialTheme.colorScheme.outlineVariant)
    val interieur: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(Dimens.Space4),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space3),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.Avatar)
                    .background(cocoon.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    phase.icone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(Dimens.IconSize),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space1),
            ) {
                Text(
                    titre,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                contenu()
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.heightIn(min = Dimens.MinTouchTarget),
            shape = forme, color = fond, contentColor = cocoon.onPhase, border = bordure,
            content = interieur,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = forme, color = fond, contentColor = cocoon.onPhase, border = bordure,
            content = interieur,
        )
    }
}

@CocoPreviews
@Composable
private fun PhaseCardPreview() {
    CocooningTheme {
        Column(
            modifier = Modifier.padding(Dimens.Space5),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space3),
        ) {
            PhaseCard(PhaseCycle.Luteale, "Inès · phase lutéale", Modifier.fillMaxWidth(), onClick = {}) {
                Text("Règles prévues dans 6 jours.", style = MaterialTheme.typography.bodyMedium)
            }
            PhaseCard(PhaseCycle.Menstruelle, "Inès · phase menstruelle", Modifier.fillMaxWidth()) {
                Text("Un peu de douceur est la bienvenue.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
