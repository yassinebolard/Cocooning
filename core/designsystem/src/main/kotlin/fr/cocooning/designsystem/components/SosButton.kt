package fr.cocooning.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.Durees
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * Bouton S.O.S : disque `sos` de 72 dp minimum, entouré d'un anneau `fond` de 5 dp.
 *
 * Il se déclenche par un appui maintenu de 2 s : l'anneau se remplit à vitesse constante et
 * un compte à rebours remplace « maintenir ». Retirer le doigt avant la fin annule sans rien
 * envoyer et affiche « Maintiens pour envoyer l'alerte ». Vibrations à 0 s, 1 s et 2 s.
 *
 * À 200 % le disque grandit avec son texte au lieu de le couper.
 *
 * TalkBack : double toucher affiche l'aide ; double toucher maintenu déclenche l'alerte
 * (action « Envoyer l'alerte »).
 *
 * @param nomPartenaire prénom annoncé par TalkBack (« Maintenir deux secondes pour alerter Yassine »).
 * @param onDeclenche appelé une seule fois, au bout des 2 s, doigt toujours posé.
 */
@Composable
fun SosButton(
    nomPartenaire: String,
    onDeclenche: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progression = remember { Animatable(0f) }
    val portee = rememberCoroutineScope()
    val haptique = LocalHapticFeedback.current
    val declencher by rememberUpdatedState(onDeclenche)
    val motion = CocooningTheme.motion
    var aideVisible by remember { mutableStateOf(false) }

    LaunchedEffect(aideVisible) {
        if (aideVisible) {
            delay(3_000)
            aideVisible = false
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SosDisque(
            progression = progression.value,
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            aideVisible = false
                            haptique.performHapticFeedback(HapticFeedbackType.LongPress)
                            var envoye = false
                            val maintien = portee.launch {
                                launch {
                                    delay(Durees.MaintienSos / 2L)
                                    haptique.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                progression.snapTo(0f)
                                progression.animateTo(1f, motion.maintienSos())
                                envoye = true
                                haptique.performHapticFeedback(HapticFeedbackType.LongPress)
                                declencher()
                            }
                            tryAwaitRelease()
                            if (!envoye) {
                                maintien.cancel()
                                aideVisible = true
                            }
                            portee.launch { progression.snapTo(0f) }
                        },
                    )
                }
                .semantics {
                    role = Role.Button
                    contentDescription = "S.O.S. Maintenir deux secondes pour alerter $nomPartenaire"
                    onClick(label = "afficher l'aide") {
                        aideVisible = true
                        true
                    }
                    onLongClick(label = "Envoyer l'alerte") {
                        declencher()
                        true
                    }
                },
        )
        if (aideVisible) {
            Text(
                text = "Maintiens pour envoyer l'alerte",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = Dimens.Space2)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Rend un nœud carré de la taille de son plus grand côté, contenu centré. */
private fun Modifier.carre(): Modifier = layout { mesurable, contraintes ->
    val p = mesurable.measure(contraintes)
    val cote = maxOf(p.width, p.height)
    layout(cote, cote) { p.place((cote - p.width) / 2, (cote - p.height) / 2) }
}

/** Dessin du bouton, sans gestion du geste : utilisé par [SosButton] et par les aperçus. */
@Composable
private fun SosDisque(progression: Float, modifier: Modifier = Modifier) {
    val cocoon = CocooningTheme.cocoon
    val fond = MaterialTheme.colorScheme.background
    val restant = ceil((1f - progression) * Durees.MaintienSos / 1000f).toInt()

    Box(
        modifier = Modifier
            .drawBehind {
                if (progression > 0f) {
                    // L'anneau de maintien occupe l'anneau de `fond` (sos sur fond : 5,3:1 clair, 3,5:1 sombre).
                    val trait = Dimens.SosHalo.toPx()
                    drawArc(
                        color = cocoon.sos,
                        startAngle = -90f,
                        sweepAngle = 360f * progression,
                        useCenter = false,
                        topLeft = Offset(trait / 2f, trait / 2f),
                        size = Size(size.width - trait, size.height - trait),
                        style = Stroke(width = trait, cap = StrokeCap.Round),
                    )
                }
            }
            .background(fond, CircleShape)
            .padding(Dimens.SosHalo),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = modifier
                .shadow(
                    elevation = Dimens.SosShadow,
                    shape = CircleShape,
                    ambientColor = cocoon.sos,
                    spotColor = cocoon.sos,
                )
                .background(cocoon.sos, CircleShape)
                .carre()
                .sizeIn(minWidth = Dimens.SosTarget, minHeight = Dimens.SosTarget)
                .padding(Dimens.Space2),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clearAndSetSemantics { },
            ) {
                Text(
                    text = "S.O.S",
                    style = MaterialTheme.typography.titleMedium,
                    color = cocoon.onSos,
                )
                Text(
                    text = if (progression > 0f) "$restant s" else "maintenir",
                    style = MaterialTheme.typography.labelSmall,
                    color = cocoon.onSos,
                )
            }
        }
    }
}

@CocoPreviews
@Composable
private fun SosButtonPreview() {
    CocooningTheme {
        Row(
            modifier = Modifier.padding(Dimens.Space5),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SosButton(nomPartenaire = "Yassine", onDeclenche = {})
            SosDisque(progression = 0.6f)
        }
    }
}
