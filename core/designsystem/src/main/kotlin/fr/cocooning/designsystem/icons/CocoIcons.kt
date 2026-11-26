package fr.cocooning.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.addPathNodes
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

/** Icône décrite par un ou plusieurs tracés SVG (`d`), mêmes règles de trait que [icone]. */
private fun iconeSvg(nom: String, vararg traces: String): ImageVector {
    val b = ImageVector.Builder(
        name = nom,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    traces.forEach { d ->
        b.addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

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

    // Icônes de navigation et de saisie

    private fun PathBuilder.rect(x1: Float, y1: Float, x2: Float, y2: Float) {
        moveTo(x1, y1); lineTo(x2, y1); lineTo(x2, y2); lineTo(x1, y2); close()
    }

    /** Accueil. */
    val Maison: ImageVector by lazy {
        icone("Maison") {
            moveTo(4f, 11f); lineTo(12f, 4f); lineTo(20f, 11f)
            moveTo(6f, 10f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 10f)
        }
    }

    /** Messages. */
    val Bulle: ImageVector by lazy {
        icone("Bulle") {
            moveTo(5f, 5f); lineTo(19f, 5f); lineTo(19f, 16f); lineTo(10f, 16f)
            lineTo(6f, 20f); lineTo(6f, 16f); lineTo(5f, 16f); close()
        }
    }

    /** Cycle : cadran avec une aiguille. */
    val Cycle: ImageVector by lazy {
        icone("Cycle") {
            cercle(12f, 12f, 8.5f)
            moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f)
        }
    }

    /** Agenda. */
    val Agenda: ImageVector by lazy {
        icone("Agenda") {
            rect(4f, 6f, 20f, 20f)
            moveTo(4f, 10f); lineTo(20f, 10f)
            moveTo(8f, 3f); lineTo(8f, 7f)
            moveTo(16f, 3f); lineTo(16f, 7f)
        }
    }

    /** Notes. */
    val Note: ImageVector by lazy {
        icone("Note") {
            rect(6f, 3f, 18f, 21f)
            moveTo(9f, 8f); lineTo(15f, 8f)
            moveTo(9f, 12f); lineTo(15f, 12f)
            moveTo(9f, 16f); lineTo(13f, 16f)
        }
    }

    /** Ajouter une pièce jointe. */
    val Plus: ImageVector by lazy {
        icone("Plus") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
    }

    /** Message vocal. */
    val Micro: ImageVector by lazy {
        icone("Micro") {
            rect(9f, 3f, 15f, 13f)
            moveTo(6f, 11f)
            arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 18f, y1 = 11f)
            moveTo(12f, 17f); lineTo(12f, 21f)
        }
    }

    /** Envoyer un message. */
    val Envoyer: ImageVector by lazy {
        icone("Envoyer") {
            moveTo(5f, 12f); lineTo(19f, 12f)
            moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
        }
    }

    // Icônes des médias

    /** Lire un message vocal. Le triangle de lecture est la seule icône pleine du système. */
    val Lecture: ImageVector by lazy {
        ImageVector.Builder(
            name = "Lecture",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = SolidColor(Color.Black),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close()
        }.build()
    }

    /** Mettre en pause. */
    val Pause: ImageVector by lazy {
        icone("Pause") {
            moveTo(9f, 5f); lineTo(9f, 19f)
            moveTo(15f, 5f); lineTo(15f, 19f)
        }
    }

    /** Fermer un message. */
    val Fermer: ImageVector by lazy {
        icone("Fermer") {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }

    /** Photo en attente d'affichage. */
    val Photo: ImageVector by lazy {
        icone("Photo") {
            rect(3f, 5f, 21f, 19f)
            cercle(9f, 10f, 2f)
            moveTo(21f, 16f); lineTo(16f, 11f); lineTo(8f, 19f)
        }
    }

    // Icônes de l'alerte S.O.S reçue (tracés repris de la fiche Bouton S.O.S)

    /** Alerte en cours : cloche. */
    val Cloche: ImageVector by lazy {
        iconeSvg("Cloche", "M6 16V11a6 6 0 0 1 12 0v5l2 2H4z", "M10 21h4")
    }

    /** Position : repère de carte. */
    val Repere: ImageVector by lazy {
        iconeSvg(
            "Repere",
            "M12 21s7-6.2 7-12a7 7 0 0 0-14 0c0 5.8 7 12 7 12z",
            "M14.5 9a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0z",
        )
    }

    /** Itinéraire vers une position. */
    val Itineraire: ImageVector by lazy {
        iconeSvg("Itineraire", "M5 19L19 5", "M9 5h10v10")
    }

    /** Appel téléphonique. */
    val Telephone: ImageVector by lazy {
        iconeSvg(
            "Telephone",
            "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z",
        )
    }

    /** Niveau de batterie. */
    val Batterie: ImageVector by lazy {
        iconeSvg("Batterie", "M3 7h15v10H3z", "M21 10v4")
    }
}
