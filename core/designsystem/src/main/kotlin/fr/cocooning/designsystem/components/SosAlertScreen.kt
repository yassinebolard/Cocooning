package fr.cocooning.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocoShapes
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Courbes
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.icons.CocoIcons

/** Où en est l'alerte vue du côté du partenaire qui la reçoit. */
enum class EtatAlerteRecue {
    /** L'alerte sonne : « J'ai vu, j'arrive » est l'action attendue. */
    Active,

    /** Le partenaire a confirmé : l'émetteur le voit (EF-SOS-07). */
    Vue,

    /** L'émetteur a arrêté l'alerte avec son code ou sa biométrie (EF-SOS-09). */
    Terminee,
}

/**
 * Dernière position connue de l'émetteur (EF-SOS-03, EF-SOS-04).
 *
 * @param adresse adresse approximative ; `null` si elle n'a pas pu être trouvée (la carte montre
 * alors seule le point exact).
 * @param precisionMetres rayon de précision du GPS.
 * @param ageSecondes temps écoulé depuis cette position.
 */
@Immutable
data class PositionAlerte(
    val adresse: String?,
    val precisionMetres: Int,
    val ageSecondes: Long,
)

/**
 * Données de l'alerte S.O.S reçue, fournies par l'appelant.
 *
 * @param prenom prénom de la personne qui a déclenché l'alerte.
 * @param ageSecondes temps écoulé depuis le déclenchement.
 * @param position `null` tant qu'aucune position n'est arrivée.
 * @param batteriePourcent niveau de batterie de l'émetteur, `null` s'il est inconnu.
 * @param test alerte d'essai (EF-SOS-10) : marquée « TEST » partout à l'écran.
 */
@Immutable
data class AlerteSosRecue(
    val prenom: String,
    val ageSecondes: Long,
    val position: PositionAlerte?,
    val batteriePourcent: Int? = null,
    val test: Boolean = false,
    val etat: EtatAlerteRecue = EtatAlerteRecue.Active,
)

/**
 * Écran plein écran de l'alerte S.O.S reçue par le partenaire (EF-SOS-05, EF-SOS-06).
 *
 * Fond `sos`, texte `sur-sos`. De haut en bas : depuis quand, « Inès a besoin d'aide », la carte,
 * l'adresse avec sa précision et la batterie, Itinéraire et Appeler (48 dp), Appeler le 112, puis
 * « J'ai vu, j'arrive » (72 dp, contour de 3 dp). Une fois vue, le gros bouton laisse place à une
 * confirmation ; une fois terminée, il devient « Fermer ».
 *
 * Lisibilité : tout le contenu défile, aucune hauteur fixe autour d'un texte, et au-delà de 130 %
 * de taille de texte les boutons passent l'un sous l'autre. L'apparition (300 ms, `courbe-entree`)
 * est coupée quand les animations du téléphone le sont ; rien ne clignote.
 *
 * TalkBack : le titre est une région `liveRegion` assertive annoncée à l'apparition et à chaque
 * changement d'état. L'âge de l'alerte, qui change chaque seconde, reste hors de cette région pour
 * ne pas répéter l'annonce.
 *
 * L'écran ne gère ni la sonnerie, ni le réveil de l'écran, ni l'affichage sur l'écran verrouillé :
 * c'est le rôle de l'activité et du service d'alarme qui l'affichent.
 *
 * @param carte carte de la position, fournie par l'appelant (bibliothèque de carte non choisie).
 * Elle reçoit une hauteur fixe ; `null` la masque.
 * @param onItineraire ouvre la navigation vers la position ; masqué tant qu'aucune position n'est connue.
 * @param onJaiVu confirme à l'émetteur que l'alerte est vue.
 * @param onFermer ferme l'écran une fois l'alerte terminée.
 */
@Composable
fun SosAlertScreen(
    alerte: AlerteSosRecue,
    onItineraire: () -> Unit,
    onAppeler: () -> Unit,
    onAppeler112: () -> Unit,
    onJaiVu: () -> Unit,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
    carte: (@Composable () -> Unit)? = null,
) {
    val cocoon = CocooningTheme.cocoon
    val motion = CocooningTheme.motion
    val apercu = LocalInspectionMode.current
    val apparition = remember { Animatable(if (apercu) 1f else 0f) }
    LaunchedEffect(Unit) { apparition.animateTo(1f, motion.moyenne(Courbes.Entree)) }
    val glissement = with(LocalDensity.current) { DecalageApparition.toPx() }
    val texteAgrandi = LocalDensity.current.fontScale >= SeuilTexteAgrandi
    val prenom = alerte.prenom

    CompositionLocalProvider(LocalContentColor provides cocoon.onSos) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(cocoon.sos)
                .graphicsLayer {
                    alpha = apparition.value
                    translationY = (1f - apparition.value) * glissement
                }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.Space5, vertical = Dimens.Space6),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space4),
        ) {
            LigneStatut(alerte)
            EnTete(alerte)

            if (carte != null && alerte.position != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HauteurCarte)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                ) { carte() }
            }

            BlocPosition(alerte)

            ActionsContact(
                prenom = prenom,
                positionConnue = alerte.position != null,
                enColonne = texteAgrandi,
                onItineraire = onItineraire,
                onAppeler = onAppeler,
                onAppeler112 = onAppeler112,
            )

            Spacer(Modifier.height(Dimens.Space2))

            when (alerte.etat) {
                EtatAlerteRecue.Active -> BoutonAlerte(
                    texte = "J'ai vu, j'arrive",
                    icone = CocoIcons.Coche,
                    onClick = onJaiVu,
                )
                EtatAlerteRecue.Vue -> ConfirmationVue(prenom)
                EtatAlerteRecue.Terminee -> BoutonAlerte(
                    texte = "Fermer",
                    icone = null,
                    onClick = onFermer,
                )
            }
        }
    }
}

private const val SeuilTexteAgrandi = 1.3f
private val HauteurCarte = 180.dp
private val DecalageApparition = 30.dp
private val ContourBoutonAlerte = 3.dp

/** « ALERTE S.O.S · il y a 12 s », avec la pastille TEST pour une alerte d'essai. */
@Composable
private fun LigneStatut(alerte: AlerteSosRecue) {
    val libelle = when {
        alerte.etat == EtatAlerteRecue.Terminee -> "ALERTE TERMINÉE"
        alerte.test -> "ALERTE D'ESSAI"
        else -> "ALERTE S.O.S"
    }
    val libelleParle = when {
        alerte.etat == EtatAlerteRecue.Terminee -> "Alerte terminée"
        alerte.test -> "Alerte d'essai"
        else -> "Alerte S.O.S"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = buildString {
                if (alerte.test) append("Test. ")
                append(libelleParle)
                append(", déclenchée ")
                append(ageParle(alerte.ageSecondes))
            }
        },
    ) {
        if (alerte.test) PastilleTest()
        Icon(CocoIcons.Cloche, contentDescription = null, modifier = Modifier.size(Dimens.IconSizeSmall))
        Text(
            text = "$libelle · ${ageCourt(alerte.ageSecondes)}",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Pastille « TEST » sur `surface` : impossible de confondre une alerte d'essai (EF-SOS-10). */
@Composable
private fun PastilleTest() {
    Text(
        text = "TEST",
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, CocoShapes.Pilule)
            .padding(horizontal = Dimens.Space3, vertical = Dimens.Space1),
    )
}

/** Titre et sous-titre : la région annoncée par TalkBack. */
@Composable
private fun EnTete(alerte: AlerteSosRecue) {
    val prenom = alerte.prenom
    val titre = when {
        alerte.etat == EtatAlerteRecue.Terminee -> "$prenom a arrêté l'alerte"
        alerte.test -> "$prenom teste l'alerte S.O.S"
        else -> "$prenom a besoin d'aide"
    }
    val sousTitre = when {
        alerte.etat == EtatAlerteRecue.Terminee -> "$prenom l'a arrêtée avec son code. Tu peux encore l'appeler."
        alerte.test -> "C'est un essai, personne n'est en danger."
        else -> null
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.Space1),
        modifier = Modifier.semantics(mergeDescendants = true) {
            heading()
            liveRegion = LiveRegionMode.Assertive
        },
    ) {
        Text(text = titre, style = MaterialTheme.typography.headlineLarge)
        if (sousTitre != null) {
            Text(text = sousTitre, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Adresse et précision, fraîcheur de la position, batterie. */
@Composable
private fun BlocPosition(alerte: AlerteSosRecue) {
    val position = alerte.position
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space2)) {
        LigneInfo(icone = CocoIcons.Repere) {
            if (position == null) {
                Text("Position en attente…", style = MaterialTheme.typography.labelLarge)
            } else {
                Column {
                    Text(
                        text = position.adresse ?: "Adresse introuvable, la carte montre le point exact",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = "Précision ${position.precisionMetres} m · mise à jour ${ageCourt(position.ageSecondes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics {
                            contentDescription = "Précision ${position.precisionMetres} mètres, " +
                                "position mise à jour ${ageParle(position.ageSecondes)}"
                        },
                    )
                }
            }
        }
        val batterie = alerte.batteriePourcent
        if (batterie != null) {
            LigneInfo(icone = CocoIcons.Batterie) {
                Text(
                    text = if (batterie <= SeuilBatterieFaible) {
                        "Batterie faible : $batterie %"
                    } else {
                        "Batterie : $batterie %"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private const val SeuilBatterieFaible = 15

@Composable
private fun LigneInfo(icone: ImageVector, contenu: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
        modifier = Modifier.semantics(mergeDescendants = true) { },
    ) {
        Icon(icone, contentDescription = null, modifier = Modifier.size(Dimens.IconSizeSmall).padding(top = 1.dp))
        Box(Modifier.weight(1f)) { contenu() }
    }
}

/**
 * Itinéraire et Appeler côte à côte (l'un sous l'autre à 200 %), puis Appeler le 112 sur toute
 * la largeur.
 */
@Composable
private fun ActionsContact(
    prenom: String,
    positionConnue: Boolean,
    enColonne: Boolean,
    onItineraire: () -> Unit,
    onAppeler: () -> Unit,
    onAppeler112: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space2)) {
        val itineraire: @Composable (Modifier) -> Unit = { m ->
            BoutonBlanc("Itinéraire", CocoIcons.Itineraire, "Itinéraire vers $prenom", onItineraire, m)
        }
        val appeler: @Composable (Modifier) -> Unit = { m ->
            BoutonBlanc("Appeler", CocoIcons.Telephone, "Appeler $prenom", onAppeler, m)
        }
        if (enColonne || !positionConnue) {
            if (positionConnue) itineraire(Modifier.fillMaxWidth())
            appeler(Modifier.fillMaxWidth())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)) {
                itineraire(Modifier.weight(1f))
                appeler(Modifier.weight(1f))
            }
        }
        BoutonBlanc("Appeler le 112", CocoIcons.Telephone, "Appeler le 112, numéro d'urgence", onAppeler112, Modifier.fillMaxWidth())
    }
}

/** Bouton `surface` de 48 dp sur le fond `sos` (5,9:1 en clair, 3,1:1 en sombre contre le fond). */
@Composable
private fun BoutonBlanc(
    texte: String,
    icone: ImageVector,
    descriptionParlee: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val focus by interactions.collectIsFocusedAsState()
    Button(
        onClick = onClick,
        modifier = modifier
            .anneauFocus(focus, CocooningTheme.cocoon.onSos, CocoShapes.Pilule)
            .heightIn(min = Dimens.MinTouchTarget)
            .semantics { contentDescription = descriptionParlee },
        shape = CocoShapes.Pilule,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = Dimens.Space4, vertical = Dimens.Space2),
        interactionSource = interactions,
    ) {
        Icon(icone, contentDescription = null, modifier = Modifier.size(Dimens.IconSizeSmall))
        Spacer(Modifier.width(Dimens.Space2))
        Text(
            text = texte,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** « J'ai vu, j'arrive » ou « Fermer » : 72 dp, contour `sur-sos` de 3 dp, fond transparent. */
@Composable
private fun BoutonAlerte(texte: String, icone: ImageVector?, onClick: () -> Unit) {
    val surSos = CocooningTheme.cocoon.onSos
    val interactions = remember { MutableInteractionSource() }
    val focus by interactions.collectIsFocusedAsState()
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .anneauFocus(focus, surSos, CocoShapes.Pilule)
            .heightIn(min = Dimens.SosTarget),
        shape = CocoShapes.Pilule,
        border = BorderStroke(ContourBoutonAlerte, surSos),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = surSos,
        ),
        contentPadding = PaddingValues(horizontal = Dimens.Space6, vertical = Dimens.Space3),
        interactionSource = interactions,
    ) {
        if (icone != null) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(Dimens.IconSize))
            Spacer(Modifier.width(Dimens.Space2))
        }
        Text(
            text = texte,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Remplace le gros bouton une fois l'alerte vue : bandeau `sos-doux` avec coche. */
@Composable
private fun ConfirmationVue(prenom: String) {
    val cocoon = CocooningTheme.cocoon
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space3),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.SosTarget)
            .background(cocoon.sosDouce, MaterialTheme.shapes.large)
            .border(ContourBoutonAlerte, cocoon.onSos, MaterialTheme.shapes.large)
            .padding(horizontal = Dimens.Space4, vertical = Dimens.Space3)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(CocoIcons.Coche, contentDescription = null, tint = cocoon.onSosDouce, modifier = Modifier.size(Dimens.IconSize))
        Text(
            text = "$prenom sait que tu as vu l'alerte et que tu arrives.",
            style = MaterialTheme.typography.labelLarge,
            color = cocoon.onSosDouce,
            modifier = Modifier.weight(1f),
        )
    }
}

/** « il y a 12 s », « il y a 3 min », « il y a 1 h 05 ». */
internal fun ageCourt(secondes: Long): String = when {
    secondes < 5 -> "à l'instant"
    secondes < 60 -> "il y a $secondes s"
    secondes < 3600 -> "il y a ${secondes / 60} min"
    else -> "il y a ${secondes / 3600} h ${(secondes % 3600 / 60).toString().padStart(2, '0')}"
}

/** Même durée en toutes lettres pour TalkBack. */
internal fun ageParle(secondes: Long): String {
    fun unite(n: Long, mot: String) = if (n > 1) "$n ${mot}s" else "$n $mot"
    return when {
        secondes < 5 -> "à l'instant"
        secondes < 60 -> "il y a ${unite(secondes, "seconde")}"
        secondes < 3600 -> "il y a ${unite(secondes / 60, "minute")}"
        else -> {
            val minutes = secondes % 3600 / 60
            val heures = unite(secondes / 3600, "heure")
            if (minutes == 0L) "il y a $heures" else "il y a $heures et ${unite(minutes, "minute")}"
        }
    }
}

// Aperçus

private val AlerteExemple = AlerteSosRecue(
    prenom = "Inès",
    ageSecondes = 12,
    position = PositionAlerte(adresse = "12 rue des Lilas, Lyon 3e", precisionMetres = 4, ageSecondes = 8),
    batteriePourcent = 23,
)

/** Faux fond de carte pour les aperçus : la vraie carte vient de l'appelant. */
@Composable
private fun CarteApercu() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .semantics { contentDescription = "Carte : position d'Inès" },
    ) {
        Icon(
            CocoIcons.Repere,
            contentDescription = null,
            tint = CocooningTheme.cocoon.sos,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
private fun ApercuAlerte(alerte: AlerteSosRecue, avecCarte: Boolean = true) {
    CocooningTheme {
        SosAlertScreen(
            alerte = alerte,
            onItineraire = {},
            onAppeler = {},
            onAppeler112 = {},
            onJaiVu = {},
            onFermer = {},
            carte = if (avecCarte) ({ CarteApercu() }) else null,
        )
    }
}

@CocoPreviews
@Preview(name = "Texte 200 %, écran entier", fontScale = 2f, widthDp = 360, heightDp = 1500)
@Composable
private fun SosAlertScreenPreview() {
    ApercuAlerte(AlerteExemple)
}

@Preview(name = "Vue", widthDp = 360, heightDp = 800)
@Preview(name = "Vue, sombre", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 800)
@Composable
private fun SosAlertScreenVuePreview() {
    ApercuAlerte(AlerteExemple.copy(ageSecondes = 74, etat = EtatAlerteRecue.Vue))
}

@Preview(name = "Position en attente, essai", widthDp = 360, heightDp = 800)
@Composable
private fun SosAlertScreenTestPreview() {
    ApercuAlerte(AlerteExemple.copy(test = true, position = null, batteriePourcent = null), avecCarte = false)
}

@Preview(name = "Terminée", widthDp = 360, heightDp = 800)
@Composable
private fun SosAlertScreenTermineePreview() {
    ApercuAlerte(AlerteExemple.copy(ageSecondes = 4_000, etat = EtatAlerteRecue.Terminee, batteriePourcent = 11))
}
