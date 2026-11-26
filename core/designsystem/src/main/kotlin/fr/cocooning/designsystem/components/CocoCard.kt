package fr.cocooning.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.icons.CocoIcons

/**
 * Carte de Cocooning : fond `surface`, filet de 1,5 dp en clair, rayon 22 dp, `ombre-1`.
 * La carte tonale (`tonale = true`) utilise `carte`, sans filet ni ombre.
 *
 * La hauteur suit le contenu : à 200 % la carte grandit. Si [onClick] est fourni, toute la
 * carte est cible (48 dp minimum). Ne pas imbriquer des cartes dans des cartes.
 */
@Composable
fun CocoCard(
    modifier: Modifier = Modifier,
    titre: String? = null,
    icone: ImageVector? = null,
    tonale: Boolean = false,
    onClick: (() -> Unit)? = null,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val cocoon = CocooningTheme.cocoon
    val couleurs = CardDefaults.cardColors(
        containerColor = if (tonale) scheme.surfaceContainer else scheme.surfaceContainerLowest,
        contentColor = scheme.onSurface,
    )
    val bordure = if (!tonale && !cocoon.sombre) BorderStroke(Dimens.Filet, scheme.outlineVariant) else null
    val elevation = CardDefaults.cardElevation(
        defaultElevation = if (tonale) Dimens.Elevation0 else Dimens.Elevation1,
    )
    val interieur: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier.padding(Dimens.Space4),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space2),
        ) {
            if (titre != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
                ) {
                    if (icone != null) {
                        Icon(icone, contentDescription = null, modifier = Modifier.size(Dimens.IconSizeSmall))
                    }
                    Text(titre, style = MaterialTheme.typography.titleMedium)
                }
            }
            contenu()
        }
    }
    val forme = MaterialTheme.shapes.large
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.heightIn(min = Dimens.MinTouchTarget),
            shape = forme, colors = couleurs, elevation = elevation, border = bordure,
            content = interieur,
        )
    } else {
        Card(
            modifier = modifier,
            shape = forme, colors = couleurs, elevation = elevation, border = bordure,
            content = interieur,
        )
    }
}

@CocoPreviews
@Composable
private fun CocoCardPreview() {
    CocooningTheme {
        Column(
            modifier = Modifier.padding(Dimens.Space5),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space3),
        ) {
            CocoCard(modifier = Modifier.fillMaxWidth(), titre = "Inès · phase lutéale", icone = CocoIcons.Lune) {
                Text(
                    "Règles prévues dans 6 jours.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CocoCard(modifier = Modifier.fillMaxWidth(), titre = "Note partagée", tonale = true, onClick = {}) {
                Text("Penser au cadeau de mamie.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
