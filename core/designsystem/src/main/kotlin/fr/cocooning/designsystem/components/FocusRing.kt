package fr.cocooning.designsystem.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import fr.cocooning.designsystem.Dimens

/**
 * Anneau de focus clavier du design système : trait plein de 3 dp, décalé de 2 dp
 * autour de la forme. Ne s'affiche qu'avec le focus clavier (les boutons ne prennent
 * pas le focus au toucher).
 */
internal fun Modifier.anneauFocus(visible: Boolean, couleur: Color, forme: Shape): Modifier =
    if (!visible) {
        this
    } else {
        drawWithContent {
            drawContent()
            val trait = Dimens.FocusRingWidth.toPx()
            val ecart = Dimens.FocusRingOffset.toPx() + trait / 2f
            val contour = forme.createOutline(
                Size(size.width + 2 * ecart, size.height + 2 * ecart),
                layoutDirection,
                this,
            )
            translate(left = -ecart, top = -ecart) {
                drawOutline(contour, couleur, style = Stroke(width = trait))
            }
        }
    }
