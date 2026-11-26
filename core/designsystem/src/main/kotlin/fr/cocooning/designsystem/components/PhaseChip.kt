package fr.cocooning.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocoShapes
import fr.cocooning.designsystem.CocoonColors
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.icons.CocoIcons

/** Les quatre phases : chacune a une couleur, une icône et un nom. */
enum class PhaseCycle(val nom: String, val icone: ImageVector) {
    Menstruelle("Phase menstruelle", CocoIcons.Goutte),
    Folliculaire("Phase folliculaire", CocoIcons.Feuille),
    Ovulatoire("Phase ovulatoire", CocoIcons.Soleil),
    Luteale("Phase lutéale", CocoIcons.Lune),
    ;

    fun couleur(c: CocoonColors): Color = when (this) {
        Menstruelle -> c.phaseMenstruelle
        Folliculaire -> c.phaseFolliculaire
        Ovulatoire -> c.phaseOvulatoire
        Luteale -> c.phaseLuteale
    }
}

/**
 * Puce d'une phase du cycle : pastille ronde avec l'icône, nom de la phase, plage de jours.
 * Texte `sur-phase` (au moins 8:1). La phase du jour reçoit une bordure `texte` de 2 dp et
 * la mention « phase du jour » pour TalkBack. Non interactive : pas de cible tactile.
 *
 * @param plageJours par exemple « J17 à J28 ».
 */
@Composable
fun PhaseChip(
    phase: PhaseCycle,
    plageJours: String,
    modifier: Modifier = Modifier,
    actuelle: Boolean = false,
) {
    val cocoon = CocooningTheme.cocoon
    val lecture = buildString {
        append(phase.nom).append(", ").append(plageJours)
        if (actuelle) append(", phase du jour")
    }
    Surface(
        color = phase.couleur(cocoon),
        contentColor = cocoon.onPhase,
        shape = CocoShapes.Pilule,
        border = if (actuelle) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
        modifier = modifier.clearAndSetSemantics { contentDescription = lecture },
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = Dimens.Space4, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(cocoon.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    phase.icone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(Dimens.IconSizeSmall),
                )
            }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(phase.nom, style = MaterialTheme.typography.labelMedium)
                Text(plageJours, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@CocoPreviews
@Composable
private fun PhaseChipPreview() {
    CocooningTheme {
        Column(
            modifier = Modifier.padding(Dimens.Space5),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space2),
        ) {
            PhaseChip(PhaseCycle.Menstruelle, "J1 à J5")
            PhaseChip(PhaseCycle.Folliculaire, "J6 à J13")
            PhaseChip(PhaseCycle.Ovulatoire, "J14 à J16")
            PhaseChip(PhaseCycle.Luteale, "J17 à J28", actuelle = true)
        }
    }
}
