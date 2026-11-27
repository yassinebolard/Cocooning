package fr.cocooning.designsystem

import androidx.compose.ui.graphics.Color

/*
 * Palettes brutes de Cocooning, recopiées du fichier tokens.json du design système
 * (direction « Lavande poudrée »). Ne pas utiliser ces valeurs directement dans un écran :
 * passer par MaterialTheme.colorScheme ou CocooningTheme.cocoon.
 * Le nom de chaque constante reprend le jeton : `sur-principale` devient `SurPrincipale`.
 */

/** Jetons du thème clair. */
internal object LightPalette {
    val Fond = Color(0xFFF6F2FC)
    val Surface = Color(0xFFFFFFFF)
    val Carte = Color(0xFFECE5F8)
    val Filet = Color(0xFFD9D0EC)
    val Contour = Color(0xFF7D7299)
    val Texte = Color(0xFF261F3B)
    val Texte2 = Color(0xFF5D5474)

    val Principale = Color(0xFF6B4FC4)
    val SurPrincipale = Color(0xFFFFFFFF)
    val PrincipaleDouce = Color(0xFFE3DAFB)
    val SurPrincipaleDouce = Color(0xFF2C1A6B)

    val Accent = Color(0xFFB23A6F)
    val SurAccent = Color(0xFFFFFFFF)
    val AccentDoux = Color(0xFFFBE0EB)
    val SurAccentDoux = Color(0xFF5C1838)

    val Sos = Color(0xFFC8102E)
    val SurSos = Color(0xFFFFFFFF)
    val SosDoux = Color(0xFFFDE3E6)
    val SurSosDoux = Color(0xFF7A0019)

    val Erreur = Color(0xFFB3261E)
    val SurErreur = Color(0xFFFFFFFF)
    val ErreurDouce = Color(0xFFF9DEDC)
    val SurErreurDouce = Color(0xFF601410)

    val Succes = Color(0xFF1E6B45)
    val SurSucces = Color(0xFFFFFFFF)
    val SuccesDoux = Color(0xFFD8F2E3)
    val SurSuccesDoux = Color(0xFF0F3D26)

    val Vigilance = Color(0xFF7A5200)
    val VigilanceDouce = Color(0xFFFCEBC4)
    val SurVigilanceDouce = Color(0xFF4A3100)

    val PhaseMenstruelle = Color(0xFFF7C9D6)
    val PhaseFolliculaire = Color(0xFFCDEBDD)
    val PhaseOvulatoire = Color(0xFFFCE2B0)
    val PhaseLuteale = Color(0xFFD8CFF6)
    val SurPhase = Color(0xFF261F3B)

    val SurfaceInverse = Color(0xFF322A47)
    val SurSurfaceInverse = Color(0xFFF2EDFB)
    val PrincipaleInverse = Color(0xFFBBA7F7)

    /** rgba(21, 17, 30, 0.56) */
    val Voile = Color(red = 21, green = 17, blue = 30, alpha = 143)
}

/** Jetons du thème sombre. Ce n'est pas une inversion : la principale s'éclaircit. */
internal object DarkPalette {
    val Fond = Color(0xFF15111E)
    val Surface = Color(0xFF211A2F)
    val Carte = Color(0xFF2C2440)
    val Filet = Color(0xFF3E3557)
    val Contour = Color(0xFF8E84AB)
    val Texte = Color(0xFFF2EDFB)
    val Texte2 = Color(0xFFBDB3D3)

    val Principale = Color(0xFFBBA7F7)
    val SurPrincipale = Color(0xFF1E1633)
    val PrincipaleDouce = Color(0xFF3D2F72)
    val SurPrincipaleDouce = Color(0xFFE8DFFF)

    val Accent = Color(0xFFF5A3C4)
    val SurAccent = Color(0xFF3A0A20)
    val AccentDoux = Color(0xFF5A2340)
    val SurAccentDoux = Color(0xFFFFD9E7)

    val Sos = Color(0xFFD11F3A)
    val SurSos = Color(0xFFFFFFFF)
    val SosDoux = Color(0xFF4A0E18)
    val SurSosDoux = Color(0xFFFFD9DD)

    val Erreur = Color(0xFFFFB4AB)
    val SurErreur = Color(0xFF690005)
    val ErreurDouce = Color(0xFF5C1610)
    val SurErreurDouce = Color(0xFFFFDAD6)

    val Succes = Color(0xFF8FE0B2)
    val SurSucces = Color(0xFF0F3D26)
    val SuccesDoux = Color(0xFF173527)
    val SurSuccesDoux = Color(0xFFC9F3DA)

    val Vigilance = Color(0xFFF5C26B)
    val VigilanceDouce = Color(0xFF3F2E08)
    val SurVigilanceDouce = Color(0xFFFCE2B0)

    val PhaseMenstruelle = Color(0xFF5A2B3D)
    val PhaseFolliculaire = Color(0xFF1F4A3B)
    val PhaseOvulatoire = Color(0xFF5A4416)
    val PhaseLuteale = Color(0xFF3C3170)
    val SurPhase = Color(0xFFF2EDFB)

    val SurfaceInverse = Color(0xFFE9E3F5)
    val SurSurfaceInverse = Color(0xFF261F3B)
    val PrincipaleInverse = Color(0xFF6B4FC4)

    /** rgba(8, 6, 12, 0.64) */
    val Voile = Color(red = 8, green = 6, blue = 12, alpha = 163)
}
