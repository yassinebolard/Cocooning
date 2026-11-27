package fr.cocooning.designsystem.components

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.icons.CocoIcons
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.TilesOverlay

/**
 * Carte de la position de l'émetteur d'une alerte S.O.S (EF-SOS-06), avec osmdroid.
 *
 * osmdroid affiche les tuiles OpenStreetMap sans aucun service Google : la carte marche aussi sur
 * un téléphone /e/OS ou sans Google Play. La mention « © OpenStreetMap contributors » reste
 * affichée, comme l'exige la licence des données.
 *
 * Un repère marque le point exact, un disque `sos` translucide montre la précision du GPS. La
 * carte se recentre à chaque nouvelle position (toutes les 10 s, EF-SOS-04). Elle se zoome à deux
 * doigts ; le défilement de l'écran d'alerte ne lui vole pas le geste. En thème sombre, les tuiles
 * sont inversées pour ne pas éblouir.
 *
 * TalkBack lit la carte comme une seule image décrite ; l'adresse et la précision sont aussi
 * écrites sous la carte, et « Itinéraire » ouvre la navigation.
 *
 * Dans un aperçu Android Studio, aucune tuile ne se charge : un fond `carte` avec le repère prend
 * la place de la carte.
 *
 * Demande la permission `INTERNET`, déclarée par ce module.
 */
@Composable
fun CarteAlerteSos(
    position: PositionAlerte,
    prenom: String,
    modifier: Modifier = Modifier,
) {
    val description = "Carte : position de $prenom, précision ${position.precisionMetres} mètres"
    if (LocalInspectionMode.current) {
        CarteRemplacement(description, modifier)
        return
    }

    val context = LocalContext.current
    val sos = CocooningTheme.cocoon.sos
    // Suit le thème de l'appli (réglage manuel compris), pas seulement celui du système.
    val sombre = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val carte = remember(context) { creerCarte(context) }
    val cycleDeVie = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(cycleDeVie, carte) {
        val observateur = LifecycleEventObserver { _, evenement ->
            when (evenement) {
                Lifecycle.Event.ON_RESUME -> carte.onResume()
                Lifecycle.Event.ON_PAUSE -> carte.onPause()
                else -> Unit
            }
        }
        cycleDeVie.addObserver(observateur)
        if (cycleDeVie.currentState.isAtLeast(Lifecycle.State.RESUMED)) carte.onResume()
        onDispose {
            cycleDeVie.removeObserver(observateur)
            carte.onPause()
            carte.onDetach()
        }
    }

    AndroidView(
        factory = { carte },
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics { contentDescription = description },
        update = { vue ->
            val point = GeoPoint(position.latitude, position.longitude)
            vue.overlayManager.tilesOverlay.setColorFilter(if (sombre) TilesOverlay.INVERT_COLORS else null)
            vue.overlays.removeAll { it is Marker || it is Polygon }

            val cercle = Polygon(vue).apply {
                points = Polygon.pointsAsCircle(point, position.precisionMetres.coerceAtLeast(1).toDouble())
                fillPaint.color = sos.copy(alpha = 0.18f).toArgb()
                outlinePaint.color = sos.toArgb()
                outlinePaint.strokeWidth = 2f * vue.resources.displayMetrics.density
                setOnClickListener { _, _, _ -> true }
            }
            val repere = Marker(vue).apply {
                this.position = point
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ -> true }
            }
            vue.overlays.add(cercle)
            vue.overlays.add(repere)
            vue.controller.setCenter(point)
            vue.invalidate()
        },
    )
}

@SuppressLint("ClickableViewAccessibility")
private fun creerCarte(context: Context): MapView {
    Configuration.getInstance().apply {
        load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        // La politique d'usage des tuiles OpenStreetMap demande un agent identifiable.
        userAgentValue = context.packageName
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        controller.setZoom(ZoomInitial)
        overlays.add(CopyrightOverlay(context))
        // Le geste sur la carte reste à la carte, pas au défilement de l'écran d'alerte.
        setOnTouchListener { vue, _ ->
            vue.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
    }
}

private const val ZoomInitial = 17.0

/** Fond `carte` avec le repère : aperçus, et tout contexte sans tuiles. */
@Composable
private fun CarteRemplacement(description: String, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Icon(
            CocoIcons.Repere,
            contentDescription = null,
            tint = CocooningTheme.cocoon.sos,
            modifier = Modifier.size(40.dp),
        )
    }
}
