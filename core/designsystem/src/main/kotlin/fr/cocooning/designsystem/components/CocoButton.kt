package fr.cocooning.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocoShapes
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.EtatAlpha
import fr.cocooning.designsystem.icons.CocoIcons

/** Styles de bouton du design système. */
enum class CocoButtonStyle {
    /** `principale` plein : l'action attendue sur l'écran, une seule par écran. */
    Principal,

    /** `principale-douce` : action secondaire importante. */
    Tonal,

    /** Bordure `contour` de 1,5 dp : alternative neutre. */
    Contour,

    /** Sans fond : annuler, fermer. */
    Texte,

    /** `erreur` plein : seulement après confirmation, jamais pour le S.O.S. */
    Destructif,
}

/**
 * Bouton en pilule de Cocooning.
 *
 * @param texte libellé qui dit l'effet (« Envoyer », « Écrire une note »), jamais « OK ».
 * @param compact 48 dp de haut pour les barres et les cartes ; sinon 56 dp (48 dp pour Texte).
 * @param chargement affiche un indicateur et [texteChargement] ; l'appui est alors ignoré.
 *
 * Le libellé peut passer sur plusieurs lignes à 200 % : seule une hauteur minimale est posée.
 */
@Composable
fun CocoButton(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CocoButtonStyle = CocoButtonStyle.Principal,
    icone: ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
    chargement: Boolean = false,
    texteChargement: String = "Envoi…",
) {
    val interactions = remember { MutableInteractionSource() }
    val focus by interactions.collectIsFocusedAsState()
    val hauteurMin = when {
        compact || style == CocoButtonStyle.Texte -> Dimens.MinTouchTarget
        else -> Dimens.ComfortTarget
    }
    val m = modifier
        .anneauFocus(focus, CocooningTheme.cocoon.focusRing, CocoShapes.Pilule)
        .heightIn(min = hauteurMin)
        .semantics { if (chargement) stateDescription = texteChargement }
    val padding = PaddingValues(horizontal = Dimens.Space6, vertical = Dimens.Space2)
    val action: () -> Unit = { if (!chargement) onClick() }
    val contenu: @Composable RowScope.() -> Unit = {
        ContenuBouton(texte = if (chargement) texteChargement else texte, icone = icone, chargement = chargement)
    }
    val colors = MaterialTheme.colorScheme

    when (style) {
        CocoButtonStyle.Principal -> Button(
            onClick = action, modifier = m, enabled = enabled, shape = CocoShapes.Pilule,
            contentPadding = padding, interactionSource = interactions, content = contenu,
        )
        CocoButtonStyle.Tonal -> FilledTonalButton(
            onClick = action, modifier = m, enabled = enabled, shape = CocoShapes.Pilule,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
            ),
            contentPadding = padding, interactionSource = interactions, content = contenu,
        )
        CocoButtonStyle.Contour -> OutlinedButton(
            onClick = action, modifier = m, enabled = enabled, shape = CocoShapes.Pilule,
            border = BorderStroke(
                Dimens.Filet,
                if (enabled) colors.outline else colors.onSurface.copy(alpha = EtatAlpha.DesactiveFond),
            ),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
            contentPadding = padding, interactionSource = interactions, content = contenu,
        )
        CocoButtonStyle.Texte -> TextButton(
            onClick = action, modifier = m, enabled = enabled, shape = CocoShapes.Pilule,
            contentPadding = PaddingValues(horizontal = Dimens.Space4, vertical = Dimens.Space2),
            interactionSource = interactions, content = contenu,
        )
        CocoButtonStyle.Destructif -> Button(
            onClick = action, modifier = m, enabled = enabled, shape = CocoShapes.Pilule,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.error,
                contentColor = colors.onError,
            ),
            contentPadding = padding, interactionSource = interactions, content = contenu,
        )
    }
}

@Composable
private fun RowScope.ContenuBouton(texte: String, icone: ImageVector?, chargement: Boolean) {
    when {
        chargement -> CircularProgressIndicator(
            modifier = Modifier.size(Dimens.IconSizeSmall),
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
        )
        icone != null -> Icon(icone, contentDescription = null, modifier = Modifier.size(Dimens.IconSizeSmall))
    }
    if (chargement || icone != null) {
        Spacer(Modifier.width(Dimens.Space2))
    }
    Text(
        text = texte,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f, fill = false),
    )
}

/**
 * Bouton icône : cible de 48 dp, `contentDescription` obligatoire.
 * @param tonal fond `principale-douce` ; sinon sans fond.
 */
@Composable
fun CocoIconButton(
    icone: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    enabled: Boolean = true,
) {
    val interactions = remember { MutableInteractionSource() }
    val focus by interactions.collectIsFocusedAsState()
    val m = modifier.anneauFocus(focus, CocooningTheme.cocoon.focusRing, CocoShapes.Pilule)
    if (tonal) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = m,
            enabled = enabled,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            interactionSource = interactions,
        ) {
            Icon(icone, contentDescription = contentDescription)
        }
    } else {
        IconButton(onClick = onClick, modifier = m, enabled = enabled, interactionSource = interactions) {
            Icon(icone, contentDescription = contentDescription)
        }
    }
}

@CocoPreviews
@Composable
private fun CocoButtonPreview() {
    CocooningTheme {
        Column(
            modifier = Modifier.padding(Dimens.Space5),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space3),
        ) {
            CocoButton("Envoyer", onClick = {}, icone = CocoIcons.Coche)
            CocoButton("Écrire une note", onClick = {}, style = CocoButtonStyle.Tonal)
            CocoButton("Voir l'agenda", onClick = {}, style = CocoButtonStyle.Contour)
            CocoButton("Envoyer", onClick = {}, chargement = true)
            CocoButton("Supprimer la note", onClick = {}, style = CocoButtonStyle.Destructif)
            CocoButton("Envoyer", onClick = {}, enabled = false)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CocoButton("Annuler", onClick = {}, style = CocoButtonStyle.Texte)
                CocoIconButton(CocoIcons.Coche, contentDescription = "Marquer comme fait", onClick = {}, tonal = true)
            }
        }
    }
}
