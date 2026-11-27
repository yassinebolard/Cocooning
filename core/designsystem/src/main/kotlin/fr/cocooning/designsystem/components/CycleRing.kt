package fr.cocooning.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import kotlin.math.cos
import kotlin.math.sin

/** Plage de jours d'une phase, bornes comprises (J1 à J5 : `premierJour = 1, dernierJour = 5`). */
@Immutable
data class SegmentCycle(
    val phase: PhaseCycle,
    val premierJour: Int,
    val dernierJour: Int,
)

/** Diamètre à taille de texte normale. Il grandit avec le texte, dans la limite de la place. */
private val DiametreBase = 220.dp

/** Au-delà de cette échelle de texte, le nom de la phase passe sous l'anneau. */
private const val SeuilTexteAgrandi = 1.3f

// Proportions de la fiche du design système, sur un dessin de 160 unités de côté.
private const val Grille = 160f
private const val Rayon = 62f
private const val EpaisseurPiste = 20f
private const val EpaisseurArc = 18f
private const val EcartSegments = 1.2f // degrés
private const val TailleIcone = 14f
private const val RayonRepere = 12f
private const val TraitRepere = 3f

/** Angle en degrés, 0 en haut, dans le sens des aiguilles d'une montre. */
private fun angleDuJour(jour: Float, longueur: Int): Float = jour / longueur * 360f

/**
 * Anneau du cycle : un arc par phase avec son icône, un repère rond sur le jour courant et le
 * jour en gros chiffre au centre. À l'apparition, l'anneau se dessine et le repère va de J1 au
 * jour courant en 450 ms (immédiat si les animations sont coupées).
 *
 * Quand le repère couvre l'icône de la phase du jour, l'icône est redessinée dans le repère.
 * À partir de 130 % de taille de texte, le nom de la phase passe sous l'anneau.
 *
 * TalkBack lit un seul élément : « Cycle de 28 jours, aujourd'hui jour 22, phase lutéale ».
 * L'anneau ne remplace pas la légende : afficher les [PhaseChip] à côté.
 *
 * @param segments phases dans l'ordre, à partir de J1.
 * @param jour jour courant. Au-delà de la longueur prévue (règles en retard), le repère reste
 * au bout de l'anneau et le vrai numéro du jour est affiché.
 */
@Composable
fun CycleRing(
    segments: List<SegmentCycle>,
    jour: Int,
    modifier: Modifier = Modifier,
) {
    val cocoon = CocooningTheme.cocoon
    val scheme = MaterialTheme.colorScheme
    val longueur = (segments.maxOfOrNull { it.dernierJour } ?: 1).coerceAtLeast(1)
    val jourPlace = jour.coerceIn(1, longueur)
    val phase = segments.firstOrNull { jourPlace in it.premierJour..it.dernierJour }?.phase
    val nomPhase = phase?.nom?.replaceFirstChar { it.lowercase() }
    val lecture = buildString {
        append("Cycle de ").append(longueur).append(" jours, aujourd'hui jour ").append(jour)
        if (nomPhase != null) append(", ").append(nomPhase)
    }

    val motion = CocooningTheme.motion
    val apercu = LocalInspectionMode.current
    val trace = remember { Animatable(if (apercu) 1f else 0f) }
    LaunchedEffect(motion) { trace.animateTo(1f, motion.longue()) }

    val icones = segments.map { rememberVectorPainter(it.phase.icone) }
    val fontScale = LocalDensity.current.fontScale
    val nomSousAnneau = fontScale >= SeuilTexteAgrandi

    BoxWithConstraints(
        modifier = modifier.clearAndSetSemantics { contentDescription = lecture },
        contentAlignment = Alignment.TopCenter,
    ) {
        val diametre = min(maxWidth, DiametreBase * fontScale.coerceIn(1f, 1.5f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.Space2),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(diametre)) {
                    // Lu ici, en phase de dessin : l'animation ne recompose rien.
                    val avancement = trace.value
                    val u = size.minDimension / Grille
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    val coin = Offset(centre.x - Rayon * u, centre.y - Rayon * u)
                    val cadre = Size(2 * Rayon * u, 2 * Rayon * u)

                    fun surAnneau(angle: Float): Offset {
                        val rad = Math.toRadians(angle - 90.0)
                        return Offset(
                            centre.x + Rayon * u * cos(rad).toFloat(),
                            centre.y + Rayon * u * sin(rad).toFloat(),
                        )
                    }

                    drawCircle(scheme.outlineVariant, Rayon * u, centre, style = Stroke(EpaisseurPiste * u))

                    segments.forEach { s ->
                        val debut = angleDuJour(s.premierJour - 1f, longueur)
                        val fin = angleDuJour(s.dernierJour.toFloat(), longueur)
                        val visible = minOf(fin, 360f * avancement) - debut - EcartSegments
                        if (visible > 0f) {
                            drawArc(
                                color = s.phase.couleur(cocoon),
                                startAngle = debut - 90f + EcartSegments / 2f,
                                sweepAngle = visible,
                                useCenter = false,
                                topLeft = coin,
                                size = cadre,
                                style = Stroke(EpaisseurArc * u),
                            )
                        }
                    }

                    // Le repère vise le milieu du jour courant.
                    val angleRepere = angleDuJour(jourPlace - 0.5f, longueur) * avancement
                    val repere = surAnneau(angleRepere)
                    var iconeDansRepere: VectorPainter? = null

                    segments.forEachIndexed { i, s ->
                        val milieu = angleDuJour((s.premierJour - 1 + s.dernierJour) / 2f, longueur)
                        if (milieu > 360f * avancement) return@forEachIndexed
                        val position = surAnneau(milieu)
                        val couverte = (position - repere).getDistance() < (RayonRepere + TailleIcone / 2f) * u
                        if (!couverte) {
                            dessinerIcone(icones[i], position, TailleIcone * u, ColorFilter.tint(cocoon.onPhase))
                        } else if (s.phase == phase) {
                            iconeDansRepere = icones[i]
                        }
                    }

                    drawCircle(scheme.background, RayonRepere * u, repere)
                    drawCircle(scheme.onBackground, RayonRepere * u, repere, style = Stroke(TraitRepere * u))
                    iconeDansRepere?.let {
                        dessinerIcone(it, repere, TailleIcone * u, ColorFilter.tint(scheme.onBackground))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("J$jour", style = MaterialTheme.typography.displayMedium, color = scheme.onBackground)
                    if (!nomSousAnneau && nomPhase != null) {
                        Text(nomPhase, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
            if (nomSousAnneau && phase != null) {
                Text(
                    phase.nom,
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onBackground,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun DrawScope.dessinerIcone(
    icone: VectorPainter,
    centre: Offset,
    cote: Float,
    teinte: ColorFilter,
) {
    translate(centre.x - cote / 2f, centre.y - cote / 2f) {
        with(icone) { draw(Size(cote, cote), colorFilter = teinte) }
    }
}

private val CycleExemple = listOf(
    SegmentCycle(PhaseCycle.Menstruelle, 1, 5),
    SegmentCycle(PhaseCycle.Folliculaire, 6, 13),
    SegmentCycle(PhaseCycle.Ovulatoire, 14, 16),
    SegmentCycle(PhaseCycle.Luteale, 17, 28),
)

@CocoPreviews
@Composable
private fun CycleRingPreview() {
    CocooningTheme {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Dimens.Space5),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space6),
        ) {
            CycleRing(CycleExemple, jour = 22, modifier = Modifier.fillMaxWidth())
            CycleRing(CycleExemple, jour = 3, modifier = Modifier.fillMaxWidth())
        }
    }
}
