package fr.cocooning.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Polices embarquées dans res/font (licence SIL OFL 1.1, voir licences/), aucun téléchargement
 * à l'exécution. Toutes les tailles et interlignes sont en sp : le texte suit le réglage
 * système jusqu'à 200 %. Ne jamais fixer la hauteur d'un bloc de texte, seulement un minimum.
 */

val Baloo2 = FontFamily(
    Font(R.font.baloo2_semibold, FontWeight.SemiBold),
    Font(R.font.baloo2_bold, FontWeight.Bold),
)

val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
)

/** L'interligne reste entier à l'agrandissement, sans rognage en haut ni en bas. */
private val InterligneComplet = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun titre(taille: Int, interligne: Int, graisse: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = Baloo2,
    fontWeight = graisse,
    fontSize = taille.sp,
    lineHeight = interligne.sp,
    lineHeightStyle = InterligneComplet,
)

private fun texte(
    taille: Int,
    interligne: Int,
    graisse: FontWeight,
    espacement: Float = 0f,
) = TextStyle(
    fontFamily = Montserrat,
    fontWeight = graisse,
    fontSize = taille.sp,
    lineHeight = interligne.sp,
    letterSpacing = espacement.em,
    lineHeightStyle = InterligneComplet,
)

// Styles du design système, nommés comme les jetons.
private val Affiche = titre(44, 52, FontWeight.Bold)
private val TitreXl = titre(32, 40)
private val TitreL = titre(28, 36)
private val TitreM = titre(22, 28)
private val TitreS = titre(18, 24)
private val CorpsL = texte(18, 28, FontWeight.Normal)
private val Corps = texte(16, 24, FontWeight.Normal)
private val CorpsS = texte(14, 20, FontWeight.Normal)
private val Legende = texte(12, 16, FontWeight.Medium, 0.01f)
private val LibelleL = texte(16, 20, FontWeight.SemiBold)
private val LibelleM = texte(14, 18, FontWeight.SemiBold)
private val LibelleS = texte(12, 16, FontWeight.SemiBold, 0.02f)

/**
 * Typographie Material 3. Les rôles non utilisés par le design système reprennent le style
 * le plus proche pour ne jamais retomber sur Roboto.
 */
val CocooningTypography = Typography(
    displayLarge = Affiche,
    displayMedium = Affiche,
    displaySmall = TitreXl,
    headlineLarge = TitreXl,
    headlineMedium = TitreL,
    headlineSmall = TitreM,
    titleLarge = TitreM,
    titleMedium = TitreS,
    titleSmall = LibelleL,
    bodyLarge = Corps,
    bodyMedium = CorpsS,
    bodySmall = Legende,
    labelLarge = LibelleL,
    labelMedium = LibelleM,
    labelSmall = LibelleS,
)

/** Styles hors Material 3, lus par `CocooningTheme.typeExtra`. */
@Immutable
class CocoonTypography(
    /** `corps-l` : introduction, contenu d'une note ouverte. */
    val corpsLecture: TextStyle = CorpsL,
)

val LocalCocoonTypography = staticCompositionLocalOf { CocoonTypography() }
