package fr.cocooning.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Rayons du design système (`rayon-xs` à `rayon-xl`). */
val CocooningShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), // rayon-xs : pastilles carrées, vignettes
    small = RoundedCornerShape(12.dp), // rayon-s : images dans une bulle, menus
    medium = RoundedCornerShape(16.dp), // rayon-m : champs, bulles
    large = RoundedCornerShape(22.dp), // rayon-l : cartes, la signature visuelle
    extraLarge = RoundedCornerShape(28.dp), // rayon-xl : feuilles du bas, dialogues
)

/** Formes hors Material 3. */
object CocoShapes {
    /** `rayon-pilule` : boutons, puces, zone de saisie, indicateur de navigation. */
    val Pilule: Shape = CircleShape

    /** Bulle reçue : coin de départ (bas gauche) à 6 dp. */
    val BulleEntrante: Shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 6.dp,
    )

    /** Bulle envoyée : coin de départ (bas droite) à 6 dp. */
    val BulleSortante: Shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp, bottomEnd = 6.dp, bottomStart = 16.dp,
    )
}
