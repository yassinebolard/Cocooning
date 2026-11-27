package fr.cocooning.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocoShapes
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.icons.CocoIcons

/**
 * Puce de choix (humeurs, filtres). Non choisie : fond `surface`, bordure `contour`.
 * Choisie : fond `principale`, texte `sur-principale` et coche à gauche : le choix n'est
 * jamais porté par la couleur seule. Le dessin fait 40 dp de haut, la zone touchable 48 dp
 * (imposée par Material 3).
 *
 * Plusieurs puces peuvent être choisies pour des humeurs, une seule pour un filtre.
 * Prévoir un `FlowRow` pour que les puces passent à la ligne, et pas plus de 8 par groupe.
 */
@Composable
fun CocoFilterChip(
    libelle: String,
    choisi: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = choisi,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 40.dp),
        shape = CocoShapes.Pilule,
        label = { Text(libelle, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = if (choisi) {
            { Icon(CocoIcons.Coche, contentDescription = null, modifier = Modifier.size(Dimens.IconSizeSmall)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = CocooningTheme.cocoon.surface,
            labelColor = scheme.onSurface,
            selectedContainerColor = scheme.primary,
            selectedLabelColor = scheme.onPrimary,
            selectedLeadingIconColor = scheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = choisi,
            borderColor = scheme.outline,
            selectedBorderColor = scheme.primary,
            borderWidth = Dimens.Filet,
            selectedBorderWidth = Dimens.Filet,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@CocoPreviews
@Composable
private fun CocoFilterChipPreview() {
    CocooningTheme {
        Column(modifier = Modifier.padding(Dimens.Space5)) {
            var choix by remember { mutableStateOf(setOf("Sereine", "Fatiguée")) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space2),
            ) {
                listOf("Sereine", "Fatiguée", "Câline", "Besoin de calme", "Joyeuse").forEach { humeur ->
                    CocoFilterChip(
                        libelle = humeur,
                        choisi = humeur in choix,
                        onClick = { choix = if (humeur in choix) choix - humeur else choix + humeur },
                    )
                }
            }
        }
    }
}
