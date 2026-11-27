package fr.cocooning.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocoShapes
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.icons.CocoIcons

enum class DirectionMessage { Entrant, Sortant }

/** Statut d'un message envoyé. Chaque statut a une icône ou un mot, jamais la couleur seule. */
enum class StatutMessage(val lu: String) {
    Envoye("envoyé"),
    Distribue("distribué"),
    Lu("lu"),
    Echec("non envoyé"),
}

/** Largeur maximale d'une bulle : 78 % de la place disponible. */
private const val LargeurMaxBulle = 0.78f

/**
 * Bulle de la messagerie (contenu texte). Reçue à gauche sur `bulle-entrante`, envoyée à
 * droite sur `bulle-sortante`. Rayon 16 dp, coin de départ à 6 dp, largeur qui suit le texte
 * jusqu'à 78 %, aucune hauteur fixe.
 *
 * TalkBack lit la bulle d'un seul tenant : « Mymy, 18 h 02 : Tu rentres à quelle heure ce soir ? ».
 *
 * @param auteur prénom lu par TalkBack.
 * @param heure heure affichée (« 18:02 »).
 * @param heureParlee heure lue par TalkBack (« 18 h 02 »).
 * @param statut seulement pour un message sortant.
 * @param onReessayer appelé quand on touche la mention d'échec.
 */
@Composable
fun MessageBubble(
    texte: String,
    direction: DirectionMessage,
    auteur: String,
    heure: String,
    modifier: Modifier = Modifier,
    heureParlee: String = heure,
    statut: StatutMessage? = null,
    onReessayer: () -> Unit = {},
) {
    val cocoon = CocooningTheme.cocoon
    val sortant = direction == DirectionMessage.Sortant
    val fond = if (sortant) cocoon.bulleSortante else cocoon.bulleEntrante
    val encre = if (sortant) cocoon.onBulleSortante else cocoon.onBulleEntrante
    // Métadonnées : en sortant, même couleur que le texte (5,9:1 clair) ; en entrant, texte-2 (5,7:1).
    val encreMeta = if (sortant) cocoon.onBulleSortante else cocoon.texteSecondaire
    val statutVisible = if (sortant) statut else null

    val lecture = buildString {
        append(auteur).append(", ").append(heureParlee).append(" : ").append(texte)
        if (statutVisible != null && statutVisible != StatutMessage.Echec) append(". ").append(statutVisible.lu)
    }

    CadreMessage(sortant, statutVisible, onReessayer, modifier) {
        Surface(
            color = fond,
            contentColor = encre,
            shape = if (sortant) CocoShapes.BulleSortante else CocoShapes.BulleEntrante,
            modifier = Modifier
                .largeurBulle()
                .clearAndSetSemantics { contentDescription = lecture },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Dimens.Space3, vertical = Dimens.Space2),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = texte,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Start),
                )
                PiedBulle(heure, statutVisible, encreMeta)
            }
        }
    }
}

/** Limite un contenu de message à 78 % de la place disponible, en suivant sa largeur réelle. */
internal fun Modifier.largeurBulle(): Modifier = layout { mesurable, contraintes ->
    val max = (contraintes.maxWidth * LargeurMaxBulle).toInt()
    val p = mesurable.measure(contraintes.copy(minWidth = 0, maxWidth = max))
    layout(p.width, p.height) { p.place(0, 0) }
}

/** Heure, mention « lu » et coches, communs à tous les types de message. */
@Composable
internal fun PiedBulle(
    heure: String,
    statut: StatutMessage?,
    encre: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space1),
    ) {
        val meta = if (statut == StatutMessage.Lu) "$heure · lu" else heure
        Text(
            meta,
            style = MaterialTheme.typography.bodySmall,
            color = encre,
            modifier = Modifier.weight(1f, fill = false),
        )
        when (statut) {
            StatutMessage.Envoye -> Icon(
                CocoIcons.Coche, contentDescription = null, tint = encre,
                modifier = Modifier.size(16.dp),
            )
            StatutMessage.Distribue, StatutMessage.Lu -> Icon(
                CocoIcons.DoubleCoche, contentDescription = null, tint = encre,
                modifier = Modifier.size(16.dp),
            )
            else -> Unit
        }
    }
}

/**
 * Rangée d'un message : aligne le contenu à droite (envoyé) ou à gauche (reçu) et ajoute
 * dessous la mention d'échec avec son action « réessayer ».
 */
@Composable
internal fun CadreMessage(
    sortant: Boolean,
    statut: StatutMessage?,
    onReessayer: () -> Unit,
    modifier: Modifier = Modifier,
    contenu: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (sortant) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Dimens.Space1),
    ) {
        contenu()
        if (statut == StatutMessage.Echec) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space1),
                modifier = Modifier
                    .heightIn(min = Dimens.MinTouchTarget)
                    .clickable(onClickLabel = "réessayer", role = Role.Button, onClick = onReessayer)
                    .padding(horizontal = Dimens.Space1),
            ) {
                Icon(
                    CocoIcons.Alerte, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp),
                )
                Text(
                    "Non envoyé · Toucher pour réessayer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@CocoPreviews
@Composable
private fun MessageBubblePreview() {
    CocooningTheme {
        Column(
            modifier = Modifier.padding(Dimens.Space5),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space2),
        ) {
            MessageBubble(
                texte = "Tu rentres à quelle heure ce soir ?",
                direction = DirectionMessage.Entrant,
                auteur = "Mymy", heure = "18:02", heureParlee = "18 h 02",
            )
            MessageBubble(
                texte = "Vers 19 h, je passe prendre du pain.",
                direction = DirectionMessage.Sortant,
                auteur = "Moi", heure = "18:04", heureParlee = "18 h 04",
                statut = StatutMessage.Lu,
            )
            MessageBubble(
                texte = "Je t'aime",
                direction = DirectionMessage.Sortant,
                auteur = "Moi", heure = "18:05", heureParlee = "18 h 05",
                statut = StatutMessage.Echec,
            )
        }
    }
}
