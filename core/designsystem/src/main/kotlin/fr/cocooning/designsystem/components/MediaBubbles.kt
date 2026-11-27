package fr.cocooning.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocoShapes
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.icons.CocoIcons

private val LargeurOnde = 88.dp
private val HauteurOnde = 28.dp
private val LargeurBarre = 3.dp
private val PasBarre = 5.dp
private const val AlphaNonEcoute = 0.45f

private val LargeurMaxVignette = 200.dp
private const val RatioVignette = 4f / 3f

/** « 0:14 » à l'écran. */
private fun dureeAffichee(secondes: Int): String =
    "${secondes / 60}:${(secondes % 60).toString().padStart(2, '0')}"

/** « 1 minute 5 secondes » pour TalkBack. */
private fun dureeParlee(secondes: Int): String {
    val minutes = secondes / 60
    val reste = secondes % 60
    fun accord(n: Int, mot: String) = "$n $mot" + if (n > 1) "s" else ""
    return when {
        minutes == 0 -> accord(reste, "seconde")
        reste == 0 -> accord(minutes, "minute")
        else -> accord(minutes, "minute") + " " + accord(reste, "seconde")
    }
}

/**
 * Bulle d'un message vocal : bouton lecture ou pause de 48 dp, forme d'onde dont la partie
 * écoutée est opaque, durée, heure et statut. La lecture audio est gérée par l'appelant.
 *
 * TalkBack lit un seul élément actionnable : « Écouter le message vocal de 14 secondes,
 * Mymy, 18 h 02 ».
 *
 * @param amplitudes volume de 0 à 1, dans l'ordre du temps. La liste est rééchantillonnée
 * selon la largeur disponible.
 * @param progression lambda de 0 à 1. Elle est lue au dessin seulement : la faire évoluer
 * pendant la lecture ne recompose pas la bulle.
 */
@Composable
fun VoiceBubble(
    dureeSecondes: Int,
    amplitudes: List<Float>,
    enLecture: Boolean,
    onLecturePause: () -> Unit,
    direction: DirectionMessage,
    auteur: String,
    heure: String,
    modifier: Modifier = Modifier,
    progression: () -> Float = { 0f },
    heureParlee: String = heure,
    statut: StatutMessage? = null,
    onReessayer: () -> Unit = {},
) {
    val cocoon = CocooningTheme.cocoon
    val sortant = direction == DirectionMessage.Sortant
    val fond = if (sortant) cocoon.bulleSortante else cocoon.bulleEntrante
    val encre = if (sortant) cocoon.onBulleSortante else cocoon.onBulleEntrante
    val encreMeta = if (sortant) cocoon.onBulleSortante else cocoon.texteSecondaire
    val statutVisible = if (sortant) statut else null
    // Sur une bulle envoyée (fond principale), le bouton inverse ses couleurs pour rester visible.
    val fondBouton = if (sortant) cocoon.onBulleSortante else MaterialTheme.colorScheme.primary
    val encreBouton = if (sortant) cocoon.bulleSortante else MaterialTheme.colorScheme.onPrimary

    val lecture = buildString {
        append(if (enLecture) "Mettre en pause" else "Écouter")
        append(" le message vocal de ").append(dureeParlee(dureeSecondes))
        append(", ").append(auteur).append(", ").append(heureParlee)
        if (statutVisible != null && statutVisible != StatutMessage.Echec) {
            append(", ").append(statutVisible.lu)
        }
    }

    CadreMessage(sortant, statutVisible, onReessayer, modifier) {
        Surface(
            color = fond,
            contentColor = encre,
            shape = if (sortant) CocoShapes.BulleSortante else CocoShapes.BulleEntrante,
            modifier = Modifier.largeurBulle(),
        ) {
            Column(
                modifier = Modifier.padding(Dimens.Space2),
                horizontalAlignment = Alignment.End,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
                ) {
                    FilledIconButton(
                        onClick = onLecturePause,
                        modifier = Modifier
                            .size(Dimens.MinTouchTarget)
                            .semantics {
                                contentDescription = lecture
                                if (enLecture) stateDescription = "lecture en cours"
                            },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = fondBouton,
                            contentColor = encreBouton,
                        ),
                    ) {
                        Icon(
                            if (enLecture) CocoIcons.Pause else CocoIcons.Lecture,
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.IconSize),
                        )
                    }
                    val droiteAGauche = LocalLayoutDirection.current == LayoutDirection.Rtl
                    Canvas(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .width(LargeurOnde)
                            .height(HauteurOnde),
                    ) {
                        val pas = PasBarre.toPx()
                        val barre = LargeurBarre.toPx()
                        val nombre = ((size.width + pas - barre) / pas).toInt()
                        if (nombre <= 0 || amplitudes.isEmpty()) return@Canvas
                        val ecoute = progression().coerceIn(0f, 1f)
                        for (i in 0 until nombre) {
                            val amplitude = amplitudes[i * amplitudes.size / nombre].coerceIn(0f, 1f)
                            val hauteur = (size.height * amplitude).coerceAtLeast(barre * 2)
                            val x = if (droiteAGauche) size.width - barre - i * pas else i * pas
                            val joue = (i + 0.5f) / nombre <= ecoute
                            drawRoundRect(
                                color = encre.copy(alpha = if (joue) 1f else AlphaNonEcoute),
                                topLeft = Offset(x, (size.height - hauteur) / 2f),
                                size = Size(barre, hauteur),
                                cornerRadius = CornerRadius(barre / 2f),
                            )
                        }
                    }
                    Text(
                        dureeAffichee(dureeSecondes),
                        style = MaterialTheme.typography.bodySmall,
                        color = encreMeta,
                        modifier = Modifier
                            .padding(end = Dimens.Space1)
                            .clearAndSetSemantics { },
                    )
                }
                // Le bouton porte déjà tout pour TalkBack : le pied n'est pas relu.
                PiedBulle(
                    heure = heure,
                    statut = statutVisible,
                    encre = encreMeta,
                    modifier = Modifier
                        .padding(horizontal = Dimens.Space1)
                        .clearAndSetSemantics { },
                )
            }
        }
    }
}

/**
 * Message image : vignette 4:3 de 200 dp de large au maximum, rayon 12 dp, poids du fichier et
 * heure dessous. Pas de bulle autour de l'image.
 *
 * Le chargement de l'image reste à l'appelant : [image] dessine dans la vignette (par exemple
 * avec `Modifier.fillMaxSize()` et un recadrage `ContentScale.Crop`). Sans [image], une icône
 * d'attente s'affiche. Le design system ne dépend ainsi d'aucune bibliothèque d'images.
 *
 * @param poids poids du fichier déjà formaté (« 2,1 Mo »).
 * @param onOuvrir ouvre la photo en grand ; toute la vignette est la cible.
 * @param description ce que montre la photo, si on le sait.
 */
@Composable
fun ImageBubble(
    poids: String,
    onOuvrir: () -> Unit,
    direction: DirectionMessage,
    auteur: String,
    heure: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    heureParlee: String = heure,
    statut: StatutMessage? = null,
    onReessayer: () -> Unit = {},
    image: (@Composable BoxScope.() -> Unit)? = null,
) {
    val cocoon = CocooningTheme.cocoon
    val scheme = MaterialTheme.colorScheme
    val sortant = direction == DirectionMessage.Sortant
    val statutVisible = if (sortant) statut else null

    val lecture = buildString {
        append(auteur).append(", ").append(heureParlee).append(" : photo")
        if (description != null) append(", ").append(description)
        append(", ").append(poids)
        if (statutVisible != null && statutVisible != StatutMessage.Echec) {
            append(", ").append(statutVisible.lu)
        }
    }

    CadreMessage(sortant, statutVisible, onReessayer, modifier) {
        Column(
            modifier = Modifier.largeurBulle(),
            horizontalAlignment = if (sortant) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(Dimens.Space1),
        ) {
            Surface(
                onClick = onOuvrir,
                shape = MaterialTheme.shapes.small,
                color = scheme.surfaceContainer,
                contentColor = scheme.onSurfaceVariant,
                border = BorderStroke(Dimens.Filet, scheme.outlineVariant),
                modifier = Modifier
                    .widthIn(max = LargeurMaxVignette)
                    .fillMaxWidth()
                    .aspectRatio(RatioVignette)
                    .semantics { contentDescription = lecture },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    if (image != null) {
                        image()
                    } else {
                        Icon(CocoIcons.Photo, contentDescription = null, modifier = Modifier.size(32.dp))
                    }
                }
            }
            // La vignette porte déjà tout pour TalkBack : cette ligne n'est pas relue.
            PiedBulle(
                heure = "Photo · $poids · $heure",
                statut = statutVisible,
                encre = cocoon.texteSecondaire,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

private val OndeExemple = listOf(
    0.2f, 0.45f, 0.65f, 0.3f, 0.55f, 0.8f, 0.4f, 0.25f, 0.5f,
    0.7f, 0.35f, 0.2f, 0.45f, 0.6f, 0.3f, 0.5f, 0.7f, 0.35f,
)

@CocoPreviews
@Composable
private fun MediaBubblesPreview() {
    CocooningTheme {
        Column(
            modifier = Modifier.padding(Dimens.Space3),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space3),
        ) {
            VoiceBubble(
                dureeSecondes = 14, amplitudes = OndeExemple, enLecture = false, onLecturePause = {},
                direction = DirectionMessage.Entrant, auteur = "Mymy",
                heure = "18:12", heureParlee = "18 h 12", progression = { 0.4f },
            )
            VoiceBubble(
                dureeSecondes = 65, amplitudes = OndeExemple, enLecture = true, onLecturePause = {},
                direction = DirectionMessage.Sortant, auteur = "Moi",
                heure = "18:14", heureParlee = "18 h 14", progression = { 0.7f },
                statut = StatutMessage.Lu,
            )
            ImageBubble(
                poids = "2,1 Mo", onOuvrir = {}, direction = DirectionMessage.Entrant,
                auteur = "Mymy", heure = "18:20", heureParlee = "18 h 20",
            )
            ImageBubble(
                poids = "840 Ko", onOuvrir = {}, direction = DirectionMessage.Sortant,
                auteur = "Moi", heure = "18:21", heureParlee = "18 h 21",
                statut = StatutMessage.Echec,
            )
        }
    }
}
