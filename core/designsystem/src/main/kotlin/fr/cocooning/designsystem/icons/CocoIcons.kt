package fr.cocooning.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/*
 * Icônes du design système : traits arrondis de 2 dp sur une grille de 24, jamais pleines.
 * Dessinées ici pour éviter la dépendance material-icons-extended. Le trait noir est
 * remplacé par la teinte de `Icon` (LocalContentColor par défaut).
 */

private fun icone(nom: String, dessin: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = nom,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = dessin,
    ).build()

private fun PathBuilder.cercle(cx: Float, cy: Float, r: Float) {
    moveTo(cx + r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx - r, y1 = cy)
    arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx + r, y1 = cy)
    close()
}

object CocoIcons {

    /** Phase menstruelle. */
    val Goutte: ImageVector by lazy {
        icone("Goutte") {
            moveTo(12f, 3f)
            curveTo(12f, 3f, 6f, 9.5f, 6f, 14f)
            arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 18f, y1 = 14f)
            curveTo(18f, 9.5f, 12f, 3f, 12f, 3f)
            close()
        }
    }

    /** Phase folliculaire. */
    val Feuille: ImageVector by lazy {
        icone("Feuille") {
            moveTo(5f, 19f)
            curveTo(5f, 10.5f, 10.5f, 5f, 19f, 5f)
            curveTo(19f, 13.5f, 13.5f, 19f, 5f, 19f)
            close()
            moveTo(5f, 19f)
            lineTo(13f, 11f)
        }
    }

    /** Phase ovulatoire. */
    val Soleil: ImageVector by lazy {
        icone("Soleil") {
            cercle(12f, 12f, 4f)
            // Huit rayons de 7 à 9,5 unités du centre.
            val rayons = listOf(
                0f to -1f, 0.7071f to -0.7071f, 1f to 0f, 0.7071f to 0.7071f,
                0f to 1f, -0.7071f to 0.7071f, -1f to 0f, -0.7071f to -0.7071f,
            )
            for ((dx, dy) in rayons) {
                moveTo(12f + dx * 7f, 12f + dy * 7f)
                lineTo(12f + dx * 9.5f, 12f + dy * 9.5f)
            }
        }
    }

    /** Phase lutéale. */
    val Lune: ImageVector by lazy {
        icone("Lune") {
            moveTo(12f, 3f)
            arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 21f, y1 = 12f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 3f)
            close()
        }
    }

    /** Choix fait, message envoyé. */
    val Coche: ImageVector by lazy {
        icone("Coche") {
            moveTo(5f, 12.5f)
            lineTo(10f, 17.5f)
            lineTo(19f, 7f)
        }
    }

    /** Message distribué ou lu. */
    val DoubleCoche: ImageVector by lazy {
        icone("DoubleCoche") {
            moveTo(2f, 12.5f)
            lineTo(7f, 17.5f)
            lineTo(16f, 7.5f)
            moveTo(12f, 16f)
            lineTo(13.5f, 17.5f)
            lineTo(22.5f, 7.5f)
        }
    }

    /** Erreur : cercle et point d'exclamation. */
    val Alerte: ImageVector by lazy {
        icone("Alerte") {
            cercle(12f, 12f, 9f)
            moveTo(12f, 7.5f)
            lineTo(12f, 13f)
            moveTo(12f, 16.5f)
            lineTo(12f, 16.51f)
        }
    }
}
