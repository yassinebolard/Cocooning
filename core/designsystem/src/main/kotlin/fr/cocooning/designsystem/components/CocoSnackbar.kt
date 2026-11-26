package fr.cocooning.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocoShapes
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.icons.CocoIcons

/** Au-delà, l'action passe sur sa propre ligne pour laisser la largeur au message. */
private const val LongueurMaxActionEnLigne = 10

private const val SeuilTexteAgrandi = 1.3f

/**
 * Message éphémère : fond `surface-inverse`, action en `principale-inverse`, rayon 12 dp.
 * Pour confirmer ou signaler quelque chose de léger (« Note supprimée », « Annuler »).
 * Jamais pour le S.O.S, ni pour une erreur qu'il faut absolument voir (utiliser [ErrorState]).
 *
 * Dans une application, passer par [CocoSnackbarHost] : il gère la durée, la file d'attente et
 * l'annonce TalkBack.
 */
@Composable
fun CocoSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    libelleAction: String? = null,
    onAction: () -> Unit = {},
    onFermer: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val texteAgrandi = LocalDensity.current.fontScale >= SeuilTexteAgrandi
    val action: (@Composable () -> Unit)? = libelleAction?.let { libelle ->
        {
            TextButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                shape = CocoShapes.Pilule,
            ) {
                Text(libelle, style = MaterialTheme.typography.labelLarge, color = scheme.inversePrimary)
            }
        }
    }
    val fermeture: (@Composable () -> Unit)? = onFermer?.let { fermer ->
        {
            IconButton(onClick = fermer) {
                Icon(CocoIcons.Fermer, contentDescription = "Fermer le message")
            }
        }
    }
    Snackbar(
        modifier = modifier.padding(Dimens.Space3),
        action = action,
        dismissAction = fermeture,
        actionOnNewLine = libelleAction != null &&
            (texteAgrandi || libelleAction.length > LongueurMaxActionEnLigne),
        shape = MaterialTheme.shapes.small,
        containerColor = scheme.inverseSurface,
        contentColor = scheme.inverseOnSurface,
        dismissActionContentColor = scheme.inverseOnSurface,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Hôte des messages éphémères, à placer dans
 * `Scaffold(snackbarHost = { CocoSnackbarHost(etat) })`.
 * Affichage : `etat.showSnackbar("Note supprimée", actionLabel = "Annuler")`.
 */
@Composable
fun CocoSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { donnees ->
        CocoSnackbar(
            message = donnees.visuals.message,
            libelleAction = donnees.visuals.actionLabel,
            onAction = donnees::performAction,
            onFermer = if (donnees.visuals.withDismissAction) donnees::dismiss else null,
        )
    }
}

@CocoPreviews
@Composable
private fun CocoSnackbarPreview() {
    CocooningTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space1)) {
            CocoSnackbar("Note enregistrée.")
            CocoSnackbar("Note supprimée.", libelleAction = "Annuler")
            CocoSnackbar(
                message = "Pas de réseau : le message partira dès que la connexion revient.",
                libelleAction = "Réessayer maintenant",
                onFermer = {},
            )
        }
    }
}
