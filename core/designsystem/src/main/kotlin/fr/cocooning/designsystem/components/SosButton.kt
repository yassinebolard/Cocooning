package fr.cocooning.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
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
 * Déclenchement en deux temps, conforme au cahier des charges (EF-SOS-02) :
 * 1. **Maintien de 3 s.** L'anneau se remplit à vitesse constante et un compte à rebours remplace
 *    « maintenir ». Retirer le doigt avant la fin annule sans rien envoyer et affiche
 *    « Maintiens pour envoyer l'alerte ». Vibrations à 0 s, 1 s, 2 s, puis forte à 3 s.
 * 2. **Compte à rebours annulable de 5 s.** Le doigt peut être retiré : l'anneau se vide, le disque
 *    affiche les secondes restantes et un bouton « Annuler l'envoi » apparaît dessous. Une
 *    vibration par seconde. Annuler revient au repos sans rien envoyer
 *    (« Alerte annulée, rien n'a été envoyé ») ; à 0 s, [onDeclenche] est appelé.
 *
 * Les deux anneaux restent animés même mouvement réduit : ils montrent du temps réel.
 * À 200 % le disque grandit avec son texte au lieu de le couper.
 *
 * TalkBack : double toucher affiche l'aide ; double toucher maintenu (action « Envoyer l'alerte »)
 * lance directement le compte à rebours de 5 s, annoncé par une région assertive, et l'action
 * « Annuler l'envoi » est proposée sur le disque comme sur le bouton.
 *
 * @param nomPartenaire prénom annoncé par TalkBack (« Maintenir trois secondes pour alerter Yassine »).
 * @param onDeclenche appelé une seule fois, à la fin du compte à rebours de 5 s, s'il n'a pas été annulé.
 * @param onAnnule appelé quand l'utilisateur annule pendant le compte à rebours.
 */
@Composable
fun SosButton(
    nomPartenaire: String,
    onDeclenche: () -> Unit,
    modifier: Modifier = Modifier,
    onAnnule: () -> Unit = {},
) {
    val maintien = remember { Animatable(0f) }
    val decompte = remember { Animatable(0f) }
    val portee = rememberCoroutineScope()
    val haptique = LocalHapticFeedback.current
    val declencher by rememberUpdatedState(onDeclenche)
    val annuler by rememberUpdatedState(onAnnule)
    val motion = CocooningTheme.motion
    var enAnnulation by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(DureeMessage)
            message = null
        }
    }

    // Deuxième temps : 5 s pendant lesquelles l'envoi peut encore être annulé.
    LaunchedEffect(enAnnulation) {
        if (enAnnulation) {
            decompte.snapTo(0f)
            val vibrations = launch {
                repeat(Durees.AnnulationSos / 1000 - 1) {
                    delay(1000)
                    haptique.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            decompte.animateTo(1f, motion.annulationSos())
            vibrations.cancel()
            haptique.performHapticFeedback(HapticFeedbackType.LongPress)
            enAnnulation = false
            decompte.snapTo(0f)
            declencher()
        }
    }

    val annulerEnvoi = {
        enAnnulation = false
        portee.launch { decompte.snapTo(0f) }
        message = "Alerte annulée, rien n'a été envoyé"
        annuler()
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SosDisque(
            maintien = maintien.value,
            decompte = if (enAnnulation) decompte.value else null,
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (enAnnulation) return@detectTapGestures
                            message = null
                            haptique.performHapticFeedback(HapticFeedbackType.LongPress)
                            var maintenu = false
                            val tache = portee.launch {
                                launch {
                                    repeat(Durees.MaintienSos / 1000 - 1) {
                                        delay(1000)
                                        haptique.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                                maintien.snapTo(0f)
                                maintien.animateTo(1f, motion.maintienSos())
                                maintenu = true
                                enAnnulation = true
                            }
                            tryAwaitRelease()
                            if (!maintenu) {
                                tache.cancel()
                                message = "Maintiens pour envoyer l'alerte"
                            }
                            portee.launch { maintien.snapTo(0f) }
                        },
                    )
                }
                .semantics {
                    role = Role.Button
                    contentDescription = if (enAnnulation) {
                        "S.O.S. Envoi de l'alerte à $nomPartenaire en cours"
                    } else {
                        "S.O.S. Maintenir trois secondes pour alerter $nomPartenaire"
                    }
                    if (enAnnulation) {
                        customActions = listOf(
                            CustomAccessibilityAction("Annuler l'envoi") { annulerEnvoi(); true },
                        )
                    } else {
                        onClick(label = "afficher l'aide") {
                            message = "Maintiens pour envoyer l'alerte"
                            true
                        }
                        onLongClick(label = "Envoyer l'alerte") {
                            message = null
                            enAnnulation = true
                            true
                        }
                    }
                },
        )
        if (enAnnulation) {
            ZoneAnnulation(nomPartenaire = nomPartenaire, onAnnuler = annulerEnvoi)
        } else if (message != null) {
            Text(
                text = message.orEmpty(),
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

private const val DureeMessage = 3_000L

/** Sous le disque pendant les 5 s : phrase annoncée par TalkBack et bouton « Annuler l'envoi ». */
@Composable
private fun ZoneAnnulation(nomPartenaire: String, onAnnuler: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Envoi de l'alerte à $nomPartenaire",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = Dimens.Space2)
                .semantics {
                    liveRegion = LiveRegionMode.Assertive
                    contentDescription = "Envoi de l'alerte à $nomPartenaire dans cinq secondes. " +
                        "Tu peux encore annuler."
                },
        )
        CocoButton(
            texte = "Annuler l'envoi",
            onClick = onAnnuler,
            style = CocoButtonStyle.Contour,
            compact = true,
            modifier = Modifier.padding(top = Dimens.Space2),
        )
    }
}

/** Rend un nœud carré de la taille de son plus grand côté, contenu centré. */
private fun Modifier.carre(): Modifier = layout { mesurable, contraintes ->
    val p = mesurable.measure(contraintes)
    val cote = maxOf(p.width, p.height)
    layout(cote, cote) { p.place((cote - p.width) / 2, (cote - p.height) / 2) }
}

/**
 * Dessin du bouton, sans gestion du geste : utilisé par [SosButton] et par les aperçus.
 *
 * @param maintien progression du maintien de 3 s, de 0 à 1 (l'anneau se remplit).
 * @param decompte progression du compte à rebours de 5 s, de 0 à 1 (l'anneau se vide) ;
 * `null` hors de ce temps.
 */
@Composable
private fun SosDisque(maintien: Float, decompte: Float?, modifier: Modifier = Modifier) {
    val cocoon = CocooningTheme.cocoon
    val fond = MaterialTheme.colorScheme.background
    val anneau = if (decompte != null) 1f - decompte else maintien
    val sousTitre = when {
        decompte != null -> "${ceil((1f - decompte) * Durees.AnnulationSos / 1000f).toInt().coerceAtLeast(1)} s"
        maintien > 0f -> "${ceil((1f - maintien) * Durees.MaintienSos / 1000f).toInt().coerceAtLeast(1)} s"
        else -> "maintenir"
    }

    Box(
        modifier = Modifier
            .drawWithContent {
                drawContent()
                // Dessiné après le fond pour rester visible : il occupe l'anneau de `fond`
                // (sos sur fond : 5,3:1 clair, 3,5:1 sombre).
                if (anneau > 0f) {
                    val trait = Dimens.SosHalo.toPx()
                    drawArc(
                        color = cocoon.sos,
                        startAngle = -90f,
                        sweepAngle = 360f * anneau,
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
                    text = if (decompte != null) "Envoi" else "S.O.S",
                    style = MaterialTheme.typography.titleMedium,
                    color = cocoon.onSos,
                )
                Text(
                    text = sousTitre,
                    style = MaterialTheme.typography.labelSmall,
                    color = cocoon.onSos,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@CocoPreviews
@Composable
private fun SosButtonPreview() {
    CocooningTheme {
        FlowRow(
            modifier = Modifier.padding(Dimens.Space5),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space4),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space4),
        ) {
            SosButton(nomPartenaire = "Yassine", onDeclenche = {})
            // Maintien, 1,8 s sur 3.
            SosDisque(maintien = 0.6f, decompte = null)
            // Compte à rebours annulable, 3 s restantes sur 5.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SosDisque(maintien = 0f, decompte = 0.4f)
                ZoneAnnulation(nomPartenaire = "Yassine", onAnnuler = {})
            }
        }
    }
}
