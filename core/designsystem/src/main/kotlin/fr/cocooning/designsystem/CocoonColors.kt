package fr.cocooning.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Rôles de couleur que Material 3 ne couvre pas : S.O.S, phases du cycle, vigilance,
 * bulles de message, anneau de focus. Lus par `CocooningTheme.cocoon`.
 *
 * Règle : `sos` et ses dérivés servent au S.O.S et à rien d'autre.
 * Une couleur de phase ne sert qu'au cycle, toujours avec son icône et son nom.
 */
@Immutable
class CocoonColors(
    val sombre: Boolean,
    val surface: Color,
    val filet: Color,
    val sos: Color,
    val onSos: Color,
    val sosDouce: Color,
    val onSosDouce: Color,
    val succes: Color,
    val vigilance: Color,
    val vigilanceDouce: Color,
    val onVigilanceDouce: Color,
    val phaseMenstruelle: Color,
    val phaseFolliculaire: Color,
    val phaseOvulatoire: Color,
    val phaseLuteale: Color,
    val onPhase: Color,
    val bulleSortante: Color,
    val onBulleSortante: Color,
    val bulleEntrante: Color,
    val onBulleEntrante: Color,
    val texteSecondaire: Color,
    val focusRing: Color,
)

internal val LightCocoonColors = CocoonColors(
    sombre = false,
    surface = LightPalette.Surface,
    filet = LightPalette.Filet,
    sos = LightPalette.Sos,
    onSos = LightPalette.SurSos,
    sosDouce = LightPalette.SosDoux,
    onSosDouce = LightPalette.SurSosDoux,
    succes = LightPalette.Succes,
    vigilance = LightPalette.Vigilance,
    vigilanceDouce = LightPalette.VigilanceDouce,
    onVigilanceDouce = LightPalette.SurVigilanceDouce,
    phaseMenstruelle = LightPalette.PhaseMenstruelle,
    phaseFolliculaire = LightPalette.PhaseFolliculaire,
    phaseOvulatoire = LightPalette.PhaseOvulatoire,
    phaseLuteale = LightPalette.PhaseLuteale,
    onPhase = LightPalette.SurPhase,
    bulleSortante = LightPalette.Principale,
    onBulleSortante = LightPalette.SurPrincipale,
    bulleEntrante = LightPalette.Carte,
    onBulleEntrante = LightPalette.Texte,
    texteSecondaire = LightPalette.Texte2,
    focusRing = LightPalette.Principale,
)

internal val DarkCocoonColors = CocoonColors(
    sombre = true,
    surface = DarkPalette.Surface,
    filet = DarkPalette.Filet,
    sos = DarkPalette.Sos,
    onSos = DarkPalette.SurSos,
    sosDouce = DarkPalette.SosDoux,
    onSosDouce = DarkPalette.SurSosDoux,
    succes = DarkPalette.Succes,
    vigilance = DarkPalette.Vigilance,
    vigilanceDouce = DarkPalette.VigilanceDouce,
    onVigilanceDouce = DarkPalette.SurVigilanceDouce,
    phaseMenstruelle = DarkPalette.PhaseMenstruelle,
    phaseFolliculaire = DarkPalette.PhaseFolliculaire,
    phaseOvulatoire = DarkPalette.PhaseOvulatoire,
    phaseLuteale = DarkPalette.PhaseLuteale,
    onPhase = DarkPalette.SurPhase,
    bulleSortante = DarkPalette.Principale,
    onBulleSortante = DarkPalette.SurPrincipale,
    bulleEntrante = DarkPalette.Carte,
    onBulleEntrante = DarkPalette.Texte,
    texteSecondaire = DarkPalette.Texte2,
    focusRing = DarkPalette.Principale,
)

val LocalCocoonColors = staticCompositionLocalOf<CocoonColors> {
    error("CocooningTheme manquant : entoure l'écran avec CocooningTheme { … }")
}
