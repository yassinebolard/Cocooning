package fr.cocooning.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview

/*
 * Correspondance jetons vers ColorScheme Material 3 (section « Correspondance Jetpack Compose »).
 * Pas de couleurs dynamiques : l'identité reste la lavande sur tous les téléphones.
 * tonalElevation reste à 0 partout : la teinte des surfaces est déjà dans les jetons.
 */

internal val CocooningLightColorScheme: ColorScheme = lightColorScheme(
    primary = LightPalette.Principale,
    onPrimary = LightPalette.SurPrincipale,
    primaryContainer = LightPalette.PrincipaleDouce,
    onPrimaryContainer = LightPalette.SurPrincipaleDouce,
    inversePrimary = LightPalette.PrincipaleInverse,
    secondary = LightPalette.Accent,
    onSecondary = LightPalette.SurAccent,
    secondaryContainer = LightPalette.AccentDoux,
    onSecondaryContainer = LightPalette.SurAccentDoux,
    tertiary = LightPalette.Succes,
    onTertiary = LightPalette.SurSucces,
    tertiaryContainer = LightPalette.SuccesDoux,
    onTertiaryContainer = LightPalette.SurSuccesDoux,
    background = LightPalette.Fond,
    onBackground = LightPalette.Texte,
    surface = LightPalette.Fond,
    onSurface = LightPalette.Texte,
    surfaceVariant = LightPalette.Carte,
    onSurfaceVariant = LightPalette.Texte2,
    surfaceTint = LightPalette.Principale,
    inverseSurface = LightPalette.SurfaceInverse,
    inverseOnSurface = LightPalette.SurSurfaceInverse,
    error = LightPalette.Erreur,
    onError = LightPalette.SurErreur,
    errorContainer = LightPalette.ErreurDouce,
    onErrorContainer = LightPalette.SurErreurDouce,
    outline = LightPalette.Contour,
    outlineVariant = LightPalette.Filet,
    scrim = LightPalette.Voile,
    surfaceBright = LightPalette.Fond,
    surfaceDim = LightPalette.Carte,
    surfaceContainerLowest = LightPalette.Surface,
    surfaceContainerLow = LightPalette.Surface,
    surfaceContainer = LightPalette.Carte,
    surfaceContainerHigh = LightPalette.Carte,
    surfaceContainerHighest = LightPalette.Carte,
)

internal val CocooningDarkColorScheme: ColorScheme = darkColorScheme(
    primary = DarkPalette.Principale,
    onPrimary = DarkPalette.SurPrincipale,
    primaryContainer = DarkPalette.PrincipaleDouce,
    onPrimaryContainer = DarkPalette.SurPrincipaleDouce,
    inversePrimary = DarkPalette.PrincipaleInverse,
    secondary = DarkPalette.Accent,
    onSecondary = DarkPalette.SurAccent,
    secondaryContainer = DarkPalette.AccentDoux,
    onSecondaryContainer = DarkPalette.SurAccentDoux,
    tertiary = DarkPalette.Succes,
    onTertiary = DarkPalette.SurSucces,
    tertiaryContainer = DarkPalette.SuccesDoux,
    onTertiaryContainer = DarkPalette.SurSuccesDoux,
    background = DarkPalette.Fond,
    onBackground = DarkPalette.Texte,
    surface = DarkPalette.Fond,
    onSurface = DarkPalette.Texte,
    surfaceVariant = DarkPalette.Carte,
    onSurfaceVariant = DarkPalette.Texte2,
    surfaceTint = DarkPalette.Principale,
    inverseSurface = DarkPalette.SurfaceInverse,
    inverseOnSurface = DarkPalette.SurSurfaceInverse,
    error = DarkPalette.Erreur,
    onError = DarkPalette.SurErreur,
    errorContainer = DarkPalette.ErreurDouce,
    onErrorContainer = DarkPalette.SurErreurDouce,
    outline = DarkPalette.Contour,
    outlineVariant = DarkPalette.Filet,
    scrim = DarkPalette.Voile,
    surfaceBright = DarkPalette.Carte,
    surfaceDim = DarkPalette.Fond,
    surfaceContainerLowest = DarkPalette.Surface,
    surfaceContainerLow = DarkPalette.Surface,
    surfaceContainer = DarkPalette.Carte,
    surfaceContainerHigh = DarkPalette.Carte,
    surfaceContainerHighest = DarkPalette.Carte,
)

/**
 * Thème de l'application. Clair et sombre suivent le système par défaut ; passer
 * `sombre = true/false` pour le réglage manuel de l'application.
 */
@Composable
fun CocooningTheme(
    sombre: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val animationsCoupees = rememberAnimationsCoupees()
    val motion = remember(animationsCoupees) { CocoMotion(animationsCoupees) }
    CompositionLocalProvider(
        LocalCocoonColors provides if (sombre) DarkCocoonColors else LightCocoonColors,
        LocalCocoonTypography provides CocoonTypography(),
        LocalCocoMotion provides motion,
    ) {
        MaterialTheme(
            colorScheme = if (sombre) CocooningDarkColorScheme else CocooningLightColorScheme,
            typography = CocooningTypography,
            shapes = CocooningShapes,
            content = content,
        )
    }
}

/** Accès aux jetons hors Material 3, sur le modèle de `MaterialTheme.colorScheme`. */
object CocooningTheme {
    val cocoon: CocoonColors
        @Composable @ReadOnlyComposable get() = LocalCocoonColors.current

    val typeExtra: CocoonTypography
        @Composable @ReadOnlyComposable get() = LocalCocoonTypography.current

    val motion: CocoMotion
        @Composable @ReadOnlyComposable get() = LocalCocoMotion.current

    val colorScheme: ColorScheme
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme

    val typography: Typography
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography
}

/**
 * Trois aperçus par composant : clair, sombre, et texte agrandi à 200 %.
 * Les fonds reprennent le jeton `fond` de chaque thème.
 */
@Preview(name = "Clair", showBackground = true, backgroundColor = 0xFFF6F2FC, widthDp = 360)
@Preview(
    name = "Sombre",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    showBackground = true,
    backgroundColor = 0xFF15111E,
    widthDp = 360,
)
@Preview(name = "Texte 200 %", fontScale = 2f, showBackground = true, backgroundColor = 0xFFF6F2FC, widthDp = 360)
annotation class CocoPreviews
