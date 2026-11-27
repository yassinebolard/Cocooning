package com.agdc.cocooning.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agdc.cocooning.di.ConteneurApp
import fr.cocooning.feature.sauvegarde.EcranBienvenue
import fr.cocooning.feature.sauvegarde.RestaurationRoute
import fr.cocooning.feature.sauvegarde.RestaurationViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Premier écran : l'accueil du premier lancement (commencer, ou restaurer une sauvegarde,
 * EF-CPT-08), puis l'application. Un téléphone qui a déjà des données va droit à l'application.
 *
 * Rien n'ouvre les bases avant la fin du premier lancement : la restauration peut donc remplacer
 * leurs fichiers sans fermer Room.
 *
 * @param onRedemarrer relance l'appli une fois la sauvegarde restaurée.
 */
@Composable
fun RacineCocooning(conteneur: ConteneurApp, onRedemarrer: () -> Unit) {
    val dependances = conteneur.dependancesSauvegarde
    // Décidé une fois au démarrage, sans suivre les préférences ensuite : une restauration remet en
    // place celles de l'ancien téléphone, qui disent « premier lancement terminé », et l'appli ne
    // doit pas basculer sur l'accueil avant d'avoir redémarré.
    var termine by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        if (termine == null) termine = dependances.suivi.premierLancementTermine.first()
    }
    when (termine) {
        // Lecture des préférences, quelques millisecondes : fond uni, sans clignotement.
        null -> Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {}
        true -> CocooningApp(conteneur)
        false -> PremierLancement(conteneur, onCommence = { termine = true }, onRedemarrer = onRedemarrer)
    }
}

@Composable
private fun PremierLancement(conteneur: ConteneurApp, onCommence: () -> Unit, onRedemarrer: () -> Unit) {
    val dependances = conteneur.dependancesSauvegarde
    val portee = rememberCoroutineScope()
    var restauration by rememberSaveable { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (restauration) {
                val vm: RestaurationViewModel = viewModel(factory = RestaurationViewModel.fabrique(dependances))
                RestaurationRoute(
                    viewModel = vm,
                    onRetour = { restauration = false },
                    onRedemarrer = onRedemarrer,
                )
            } else {
                EcranBienvenue(
                    onCommencer = {
                        portee.launch {
                            dependances.suivi.terminerPremierLancement()
                            onCommence()
                        }
                    },
                    onRestaurer = { restauration = true },
                )
            }
        }
    }
}
