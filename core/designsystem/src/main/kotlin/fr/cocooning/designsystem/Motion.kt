package fr.cocooning.designsystem

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/** Durées du design système, en millisecondes. Rien ne dépasse 450 ms, sauf le maintien S.O.S. */
object Durees {
    /** `duree-micro` : retour de pression, couleur d'une puce. */
    const val Micro = 100
    /** `duree-courte` : badge, interrupteur, nouvelle bulle. */
    const val Courte = 200
    /** `duree-moyenne` : onglet, carte qui s'ouvre, feuille du bas. */
    const val Moyenne = 300
    /** `duree-longue` : transition entre écrans, anneau du cycle. */
    const val Longue = 450
    /** `duree-maintien-sos` : appui maintenu pour déclencher le S.O.S. */
    const val MaintienSos = 2000
}

/** Courbes du design système. */
object Courbes {
    /** `courbe-standard` (M3 emphasized). */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** `courbe-entree` : ce qui entre à l'écran. */
    val Entree: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** `courbe-sortie` : ce qui sort de l'écran. */
    val Sortie: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    /** `courbe-lineaire` : seulement l'anneau de maintien S.O.S, qui montre le temps réel. */
    val Lineaire: Easing = LinearEasing
}

/**
 * Point unique de la règle de réduction du mouvement. Lu par `CocooningTheme.motion`.
 * Quand les animations sont coupées sur le téléphone, chaque spec devient `snap()` :
 * aucun composant n'a à tester le réglage lui-même.
 */
@Immutable
class CocoMotion(val animationsCoupees: Boolean) {

    fun <T> spec(duree: Int, courbe: Easing = Courbes.Standard): FiniteAnimationSpec<T> =
        if (animationsCoupees) snap() else tween(durationMillis = duree, easing = courbe)

    fun <T> micro(): FiniteAnimationSpec<T> = spec(Durees.Micro)
    fun <T> courte(courbe: Easing = Courbes.Standard): FiniteAnimationSpec<T> = spec(Durees.Courte, courbe)
    fun <T> moyenne(courbe: Easing = Courbes.Standard): FiniteAnimationSpec<T> = spec(Durees.Moyenne, courbe)
    fun <T> longue(courbe: Easing = Courbes.Standard): FiniteAnimationSpec<T> = spec(Durees.Longue, courbe)

    /**
     * Anneau de maintien S.O.S : il reste animé même mouvement réduit, car il montre le temps
     * réel du maintien (2 s) et non une décoration.
     */
    fun <T> maintienSos(): FiniteAnimationSpec<T> =
        tween(durationMillis = Durees.MaintienSos, easing = Courbes.Lineaire)
}

val LocalCocoMotion = staticCompositionLocalOf { CocoMotion(animationsCoupees = false) }

private fun lireAnimationsCoupees(context: Context): Boolean =
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f

/** Suit en direct le réglage « Échelle d'animation » / « Supprimer les animations ». */
@Composable
internal fun rememberAnimationsCoupees(): Boolean {
    val context = LocalContext.current
    val apercu = LocalInspectionMode.current
    var coupees by remember(context) {
        mutableStateOf(if (apercu) false else lireAnimationsCoupees(context))
    }
    DisposableEffect(context, apercu) {
        if (apercu) return@DisposableEffect onDispose { }
        val observateur = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                coupees = lireAnimationsCoupees(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observateur,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observateur) }
    }
    return coupees
}
