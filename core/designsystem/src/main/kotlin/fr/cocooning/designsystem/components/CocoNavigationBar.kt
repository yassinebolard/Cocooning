package fr.cocooning.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocoShapes
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.icons.CocoIcons

/**
 * Une destination de premier niveau.
 * @param badge nombre de non lus ; 0 n'affiche rien.
 */
data class DestinationNav(
    val libelle: String,
    val icone: ImageVector,
    val badge: Int = 0,
)

/** Les cinq destinations de premier niveau : Accueil, Messages, Cycle, Agenda, Notes. */
fun destinationsPrincipales(nonLus: Int = 0) = listOf(
    DestinationNav("Accueil", CocoIcons.Maison),
    DestinationNav("Messages", CocoIcons.Bulle, badge = nonLus),
    DestinationNav("Cycle", CocoIcons.Cycle),
    DestinationNav("Agenda", CocoIcons.Agenda),
    DestinationNav("Notes", CocoIcons.Note),
)

/**
 * Barre de navigation du bas : fond `surface`, icône et libellé visibles, indicateur
 * `principale-douce` qui change de couleur en 300 ms. Elle fait au moins 80 dp et grandit avec
 * le texte agrandi.
 *
 * Dérogation validée au design système (« libellé toujours affiché ») : quand un libellé ne tient
 * plus dans sa part de largeur (texte agrandi, vers 130 % et toujours à 200 %), il serait coupé au
 * milieu d'un mot. Seul l'onglet actif garde alors son libellé, sur une ligne, et prend la place
 * dont il a besoin ; les autres n'affichent que leur icône. TalkBack lit toujours le libellé de
 * chaque onglet.
 *
 * Écrite à la main (plutôt que `NavigationBar`) parce que celle de Material 3 a une hauteur
 * fixe de 80 dp, contraire à la règle « aucune hauteur fixe autour d'un texte ».
 */
@Composable
fun CocoNavigationBar(
    destinations: List<DestinationNav>,
    selection: Int,
    onSelection: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = CocooningTheme.cocoon.surface,
        shadowElevation = Dimens.Elevation2,
        modifier = modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            val styleLibelle = MaterialTheme.typography.labelSmall
            val mesureur = rememberTextMeasurer()
            val densite = LocalDensity.current
            // Place d'un libellé quand les cinq onglets se partagent la largeur.
            val nombre = destinations.size.coerceAtLeast(1)
            val largeurLibelle = (maxWidth - Dimens.Space2 * 2 - Dimens.Space1 * (nombre - 1)) / nombre
            val compacte = with(densite) {
                destinations.any { mesureur.measure(it.libelle, styleLibelle).size.width > largeurLibelle.toPx() }
            }
            Row(
                modifier = Modifier
                    .heightIn(min = Dimens.NavBarHeight)
                    .padding(horizontal = Dimens.Space2, vertical = Dimens.Space2)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space1),
            ) {
                destinations.forEachIndexed { index, destination ->
                    val selectionne = index == selection
                    ItemNavigation(
                        destination = destination,
                        selectionne = selectionne,
                        libelleVisible = !compacte || selectionne,
                        onClick = { onSelection(index) },
                        // En mode compact, l'onglet actif est mesuré d'abord à la taille de son libellé,
                        // les autres se partagent le reste.
                        modifier = if (compacte && selectionne) Modifier else Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemNavigation(
    destination: DestinationNav,
    selectionne: Boolean,
    libelleVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = CocooningTheme.motion
    val fondIndicateur by animateColorAsState(
        targetValue = if (selectionne) scheme.primaryContainer else Color.Transparent,
        animationSpec = motion.moyenne(),
        label = "indicateur",
    )
    val couleurIcone = if (selectionne) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    val couleurTexte = if (selectionne) scheme.primary else scheme.onSurfaceVariant

    Column(
        modifier = modifier
            .heightIn(min = Dimens.MinTouchTarget)
            .selectable(selected = selectionne, onClick = onClick, role = Role.Tab)
            .semantics(mergeDescendants = true) {
                if (!libelleVisible) contentDescription = destination.libelle
                if (destination.badge > 0) stateDescription = "${destination.badge} non lus"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(32.dp)
                // Pas de `clip` : le badge déborde de l'indicateur sans être rogné.
                .background(fondIndicateur, CocoShapes.Pilule),
            contentAlignment = Alignment.Center,
        ) {
            BadgedBox(
                badge = {
                    if (destination.badge > 0) {
                        // Le nombre grandit avec le texte jusqu'à 150 % seulement : au-delà, le badge
                        // couvrirait l'icône voisine. TalkBack l'annonce en entier (« 3 non lus »).
                        val densite = LocalDensity.current
                        CompositionLocalProvider(
                            LocalDensity provides Density(densite.density, densite.fontScale.coerceAtMost(1.5f)),
                        ) {
                            Badge(containerColor = scheme.secondary, contentColor = scheme.onSecondary) {
                                Text(if (destination.badge > 99) "99+" else destination.badge.toString())
                            }
                        }
                    }
                },
            ) {
                Icon(
                    destination.icone,
                    contentDescription = null,
                    tint = couleurIcone,
                    modifier = Modifier.size(Dimens.IconSize),
                )
            }
        }
        if (libelleVisible) {
            Text(
                text = destination.libelle,
                style = MaterialTheme.typography.labelSmall,
                color = couleurTexte,
                textAlign = TextAlign.Center,
                // Jamais coupé au milieu d'un mot : un mot par ligne au plus.
                softWrap = false,
                modifier = Modifier.padding(top = Dimens.Space1),
            )
        }
    }
}

@CocoPreviews
@Composable
private fun CocoNavigationBarPreview() {
    CocooningTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space3)) {
            CocoNavigationBar(destinations = destinationsPrincipales(nonLus = 3), selection = 1, onSelection = {})
            CocoNavigationBar(destinations = destinationsPrincipales(nonLus = 128), selection = 3, onSelection = {})
        }
    }
}
