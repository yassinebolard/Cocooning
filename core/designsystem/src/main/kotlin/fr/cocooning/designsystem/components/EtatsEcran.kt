package fr.cocooning.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocoShapes
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.icons.CocoIcons

/**
 * Écran ou bloc vide : disque `carte` avec une icône, un titre, une phrase qui dit quoi faire,
 * et une action facultative. Centré, sans hauteur fixe.
 *
 * Exemple : titre « Aucune note pour l'instant », message « Écris la première, elle sera
 * visible par vous deux. », action « Écrire une note ».
 */
@Composable
fun EmptyState(
    titre: String,
    message: String,
    modifier: Modifier = Modifier,
    icone: ImageVector = CocoIcons.Note,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.Space5, vertical = Dimens.Space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.Space3),
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.SosTarget)
                .background(MaterialTheme.colorScheme.surfaceContainer, CocoShapes.Pilule),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
        Text(
            titre,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        if (action != null) action()
    }
}

/**
 * Bandeau d'erreur : fond `erreur-douce`, icône et texte (la couleur ne porte jamais seule
 * l'erreur). Dit ce qui s'est passé et quoi faire, sans excuse ni code. Annoncé par TalkBack.
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    libelleAction: String = "Réessayer",
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.errorContainer,
        contentColor = scheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(
            modifier = Modifier.padding(Dimens.Space4),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space3),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space2)) {
                Icon(CocoIcons.Alerte, contentDescription = null, modifier = Modifier.size(Dimens.IconSize))
                Text(message, style = MaterialTheme.typography.bodyLarge)
            }
            if (onAction != null) {
                CocoButton(
                    texte = libelleAction,
                    onClick = onAction,
                    style = CocoButtonStyle.Contour,
                    compact = true,
                )
            }
        }
    }
}

/**
 * Squelette de chargement : blocs `carte` traversés par un reflet de 1,4 s en boucle, avec le
 * texte « Chargement… » pour TalkBack. Si les animations sont coupées sur le téléphone, le reflet
 * disparaît et les blocs restent fixes.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    lignes: Int = 3,
) {
    val scheme = MaterialTheme.colorScheme
    val fixe = CocooningTheme.motion.animationsCoupees
    val reflet = if (fixe) {
        null
    } else {
        val transition = rememberInfiniteTransition(label = "reflet")
        transition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
            label = "reflet",
        ).value
    }
    val brosse = if (reflet == null) {
        Brush.linearGradient(listOf(scheme.surfaceContainer, scheme.surfaceContainer))
    } else {
        Brush.linearGradient(
            colors = listOf(scheme.surfaceContainer, scheme.surfaceContainerLowest, scheme.surfaceContainer),
            start = Offset(reflet * 600f, 0f),
            end = Offset(reflet * 600f + 400f, 0f),
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Dimens.Space3),
    ) {
        // Lu par TalkBack ; visible seulement si le mouvement est coupé.
        Text(
            "Chargement…",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        repeat(lignes) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(brosse)
                    .clearAndSetSemantics { },
            )
        }
    }
}

@CocoPreviews
@Composable
private fun EtatsEcranPreview() {
    CocooningTheme {
        Column(
            modifier = Modifier.padding(Dimens.Space5),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space6),
        ) {
            EmptyState(
                titre = "Aucune note pour l'instant",
                message = "Écris la première, elle sera visible par vous deux.",
                action = { CocoButton("Écrire une note", onClick = {}) },
            )
            ErrorState(
                message = "Le message n'est pas parti : pas de réseau. Vérifie ta connexion puis réessaie.",
                onAction = {},
            )
            LoadingState()
        }
    }
}
