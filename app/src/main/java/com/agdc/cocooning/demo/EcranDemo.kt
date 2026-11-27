package com.agdc.cocooning.demo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.components.AlerteSosRecue
import fr.cocooning.designsystem.components.CocoButton
import fr.cocooning.designsystem.components.CocoButtonStyle
import fr.cocooning.designsystem.components.CocoCard
import fr.cocooning.designsystem.components.CocoFilterChip
import fr.cocooning.designsystem.components.CocoNavigationBar
import fr.cocooning.designsystem.components.CocoSnackbarHost
import fr.cocooning.designsystem.components.CycleRing
import fr.cocooning.designsystem.components.EmptyState
import fr.cocooning.designsystem.components.EtatAlerteRecue
import fr.cocooning.designsystem.components.PhaseChip
import fr.cocooning.designsystem.components.PhaseCycle
import fr.cocooning.designsystem.components.PositionAlerte
import fr.cocooning.designsystem.components.SegmentCycle
import fr.cocooning.designsystem.components.SosAlertScreen
import fr.cocooning.designsystem.components.SosButton
import fr.cocooning.designsystem.components.destinationsPrincipales
import fr.cocooning.designsystem.icons.CocoIcons
import kotlinx.coroutines.launch

/*
 * Écran de démonstration du design système, sans aucune fonctionnalité métier :
 * il sert seulement à voir le thème et les composants sur un vrai téléphone.
 * Les données affichées (prénoms, cycle, position) sont fictives.
 */

private val CycleDemo = listOf(
    SegmentCycle(PhaseCycle.Menstruelle, 1, 5),
    SegmentCycle(PhaseCycle.Folliculaire, 6, 13),
    SegmentCycle(PhaseCycle.Ovulatoire, 14, 16),
    SegmentCycle(PhaseCycle.Luteale, 17, 28),
)

private val AlerteDemo = AlerteSosRecue(
    prenom = "Inès",
    ageSecondes = 12,
    position = PositionAlerte(
        latitude = 45.7578,
        longitude = 4.8320,
        adresse = "12 rue des Lilas, Lyon 3e",
        precisionMetres = 4,
        ageSecondes = 8,
    ),
    batteriePourcent = 23,
    test = true,
)

@Composable
fun EcranDemo(
    sombre: Boolean,
    onSombreChange: (Boolean) -> Unit,
) {
    var onglet by rememberSaveable { mutableIntStateOf(0) }
    var alerteVisible by rememberSaveable { mutableStateOf(false) }
    var alerteVue by rememberSaveable { mutableStateOf(false) }
    val messages = remember { SnackbarHostState() }
    val portee = rememberCoroutineScope()
    val annoncer: (String) -> Unit = { texte ->
        portee.launch {
            messages.currentSnackbarData?.dismiss()
            messages.showSnackbar(texte)
        }
    }

    if (alerteVisible) {
        val fermer = {
            alerteVisible = false
            alerteVue = false
        }
        BackHandler(onBack = fermer)
        Scaffold { marges ->
            SosAlertScreen(
                alerte = AlerteDemo.copy(
                    etat = if (alerteVue) EtatAlerteRecue.Vue else EtatAlerteRecue.Active,
                ),
                onItineraire = { annoncer("Démonstration : l'itinéraire n'est pas encore branché") },
                onAppeler = { annoncer("Démonstration : l'appel n'est pas encore branché") },
                onAppeler112 = { annoncer("Démonstration : aucun appel au 112 n'est passé") },
                onJaiVu = { alerteVue = true },
                onFermer = fermer,
                modifier = Modifier.padding(marges),
            )
        }
        return
    }

    val destinations = destinationsPrincipales(nonLus = 2)
    Scaffold(
        bottomBar = {
            CocoNavigationBar(
                destinations = destinations,
                selection = onglet,
                onSelection = { onglet = it },
            )
        },
        snackbarHost = { CocoSnackbarHost(messages) },
    ) { marges ->
        Box(Modifier.fillMaxSize().padding(marges)) {
            if (onglet == 0) {
                OngletAccueil(
                    sombre = sombre,
                    onSombreChange = onSombreChange,
                    onAnnoncer = annoncer,
                    onVoirAlerte = { alerteVisible = true },
                )
            } else {
                EmptyState(
                    titre = destinations[onglet].libelle,
                    message = "Cet onglet n'existe pas encore. La démonstration se trouve sur l'accueil.",
                    icone = destinations[onglet].icone,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun OngletAccueil(
    sombre: Boolean,
    onSombreChange: (Boolean) -> Unit,
    onAnnoncer: (String) -> Unit,
    onVoirAlerte: () -> Unit,
) {
    var phaseChoisie by rememberSaveable { mutableStateOf(PhaseCycle.Luteale) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.Space4, vertical = Dimens.Space6),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space6),
    ) {
        Text(
            text = "Cocooning",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Démonstration du design système Lavande poudrée. Aucune donnée réelle n'est utilisée.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        CocoCard(titre = "Apparence", icone = if (sombre) CocoIcons.Lune else CocoIcons.Soleil) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)) {
                CocoFilterChip(libelle = "Clair", choisi = !sombre, onClick = { onSombreChange(false) })
                CocoFilterChip(libelle = "Sombre", choisi = sombre, onClick = { onSombreChange(true) })
            }
        }

        CocoCard(titre = "Bouton S.O.S", icone = CocoIcons.Alerte) {
            Text(
                text = "Maintenir 3 secondes, puis 5 secondes pour annuler. Rien n'est envoyé : c'est une démonstration.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Dimens.Space4))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SosButton(
                    nomPartenaire = "Inès",
                    onDeclenche = { onAnnoncer("Démonstration : l'alerte aurait été envoyée") },
                    onAnnule = { onAnnoncer("Alerte annulée, rien n'a été envoyé") },
                )
            }
            Spacer(Modifier.height(Dimens.Space4))
            CocoButton(
                texte = "Voir l'alerte S.O.S reçue",
                onClick = onVoirAlerte,
                style = CocoButtonStyle.Tonal,
                icone = CocoIcons.Repere,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        CocoCard(titre = "Cycle", icone = CocoIcons.Cycle) {
            CycleRing(CycleDemo, jour = 22, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Dimens.Space4))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space2),
            ) {
                CycleDemo.forEach { segment ->
                    PhaseChip(
                        phase = segment.phase,
                        plageJours = "J${segment.premierJour} à J${segment.dernierJour}",
                        actuelle = segment.phase == phaseChoisie,
                    )
                }
            }
        }

        CocoCard(titre = "Boutons", icone = CocoIcons.Coche) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space3)) {
                CocoButton("Principal", onClick = { onAnnoncer("Bouton principal") }, modifier = Modifier.fillMaxWidth())
                CocoButton("Tonal", onClick = { onAnnoncer("Bouton tonal") }, style = CocoButtonStyle.Tonal, modifier = Modifier.fillMaxWidth())
                CocoButton("Contour", onClick = { onAnnoncer("Bouton contour") }, style = CocoButtonStyle.Contour, modifier = Modifier.fillMaxWidth())
                CocoButton("Texte", onClick = { onAnnoncer("Bouton texte") }, style = CocoButtonStyle.Texte, modifier = Modifier.fillMaxWidth())
            }
        }

        CocoCard(titre = "Filtres", icone = CocoIcons.Note, tonale = true) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space2),
            ) {
                CycleDemo.forEach { segment ->
                    CocoFilterChip(
                        libelle = segment.phase.nom,
                        choisi = segment.phase == phaseChoisie,
                        onClick = { phaseChoisie = segment.phase },
                    )
                }
            }
        }
    }
}
