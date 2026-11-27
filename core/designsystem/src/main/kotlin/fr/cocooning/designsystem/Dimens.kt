package fr.cocooning.designsystem

import androidx.compose.ui.unit.dp

/**
 * Espacements (grille de 4 dp), tailles de cible, icônes et élévations du design système.
 * Les `px` du système sont des `dp` ici.
 */
object Dimens {
    // Espacements
    /** `espace-1` : icône et texte serrés. */
    val Space1 = 4.dp
    /** `espace-2` : icône et libellé dans un bouton, entre puces, entre bulles d'un même envoi. */
    val Space2 = 8.dp
    /** `espace-3` : entre cartes, entre bulles de deux auteurs. */
    val Space3 = 12.dp
    /** `espace-4` : padding intérieur des cartes et des champs. */
    val Space4 = 16.dp
    /** `espace-5` : marge latérale des écrans. */
    val Space5 = 20.dp
    /** `espace-6` : padding horizontal des boutons, écart entre sections. */
    val Space6 = 24.dp
    /** `espace-8` : respiration au-dessus d'un titre d'écran. */
    val Space8 = 32.dp
    /** `espace-12` : grands blancs. */
    val Space12 = 48.dp

    // Cibles tactiles
    /** `cible-min` : zone touchable minimale de tout élément interactif. */
    val MinTouchTarget = 48.dp
    /** `cible-confort` : bouton principal et champs. */
    val ComfortTarget = 56.dp
    /** `cible-sos` : bouton S.O.S et « J'ai vu, j'arrive ». */
    val SosTarget = 72.dp

    // Tailles
    val IconSize = 24.dp
    val IconSizeSmall = 20.dp
    val Avatar = 40.dp
    val NavBarHeight = 80.dp

    // Traits
    /** Filet des cartes et bordure des boutons contour. */
    val Filet = 1.5.dp
    /** Anneau de focus clavier : 3 dp plein, décalé de 2 dp. */
    val FocusRingWidth = 3.dp
    val FocusRingOffset = 2.dp
    /** Anneau de `fond` autour du bouton S.O.S. */
    val SosHalo = 5.dp

    // Élévations (shadowElevation ; tonalElevation reste toujours à 0)
    val Elevation0 = 0.dp
    val Elevation1 = 1.dp
    val Elevation2 = 3.dp
    val Elevation3 = 6.dp
    /** `ombre-sos` : à utiliser seulement sous le bouton S.O.S. */
    val SosShadow = 10.dp
}

/** Opacités d'état (calques et éléments désactivés). */
object EtatAlpha {
    const val Survol = 0.08f
    const val Focus = 0.12f
    const val Pression = 0.12f
    const val DesactiveContenu = 0.38f
    const val DesactiveFond = 0.12f
}
