package com.agdc.cocooning.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.agdc.cocooning.di.ConteneurApp
import com.agdc.cocooning.navigation.CalendrierCle
import com.agdc.cocooning.navigation.CycleCle
import com.agdc.cocooning.navigation.MessagesCle
import com.agdc.cocooning.navigation.NavigationPrincipale
import com.agdc.cocooning.navigation.NotesCle
import com.agdc.cocooning.navigation.OngletsPrincipaux
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.components.CocoNavigationBar
import fr.cocooning.designsystem.components.CocoSnackbarHost
import fr.cocooning.designsystem.components.SosButton
import fr.cocooning.feature.accueil.AccueilCle
import fr.cocooning.feature.accueil.AccueilRoute
import fr.cocooning.feature.accueil.AccueilViewModel
import fr.cocooning.feature.accueil.ActionsAccueil
import kotlinx.coroutines.launch

/**
 * Coquille de l'application : barre de navigation, bouton S.O.S flottant, messages éphémères et
 * affichage de l'écran du dessus de la pile.
 *
 * Le bouton S.O.S flotte au-dessus de la barre sur tous les onglets (EF-SOS-01, fiche
 * BoutonSOS du design système) : un seul geste depuis n'importe quel écran de premier niveau.
 */
@Composable
fun CocooningApp(conteneur: ConteneurApp) {
    val pile = rememberNavBackStack(OngletsPrincipaux.depart)
    val navigation = remember(pile) { NavigationPrincipale(pile) }
    val nonLus by conteneur.messagesNonLus.collectAsStateWithLifecycle()
    val destinations = OngletsPrincipaux.destinations(nonLus)

    val messages = remember { SnackbarHostState() }
    val portee = rememberCoroutineScope()
    val annoncer: (String) -> Unit = { texte ->
        portee.launch {
            messages.currentSnackbarData?.dismiss()
            messages.showSnackbar(texte)
        }
    }

    // Le contenu laisse sous lui la hauteur réelle du bouton S.O.S, qui grandit avec le texte.
    var hauteurSos by remember { mutableIntStateOf(0) }
    val margeSos = PaddingValues(bottom = with(LocalDensity.current) { hauteurSos.toDp() } + Dimens.Space4)

    val motion = CocooningTheme.motion
    val fondu: () -> ContentTransform = { fadeIn(motion.moyenne()) togetherWith fadeOut(motion.moyenne()) }

    val actionsAccueil = ActionsAccueil(
        onOuvrirMessages = { navigation.ouvrirOnglet(MessagesCle) },
        onOuvrirCycle = { navigation.ouvrirOnglet(CycleCle) },
        onOuvrirCalendrier = { navigation.ouvrirOnglet(CalendrierCle) },
        onOuvrirNotes = { navigation.ouvrirOnglet(NotesCle) },
        onAnnoncer = annoncer,
    )

    Scaffold(
        bottomBar = {
            CocoNavigationBar(
                destinations = destinations,
                selection = navigation.indexOngletCourant,
                onSelection = navigation::ouvrirOnglet,
            )
        },
        floatingActionButton = {
            SosButton(
                nomPartenaire = conteneur.prenomPartenaire,
                onDeclenche = {
                    annoncer("Alerte d'essai : rien n'est encore relié, aucune alerte n'est partie.")
                },
                modifier = Modifier.onSizeChanged { hauteurSos = it.height },
                alignement = Alignment.End,
            )
        },
        snackbarHost = { CocoSnackbarHost(messages) },
    ) { marges ->
        NavDisplay(
            backStack = pile,
            onBack = { navigation.retour() },
            modifier = Modifier
                .padding(marges)
                .consumeWindowInsets(marges),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            transitionSpec = { fondu() },
            popTransitionSpec = { fondu() },
            predictivePopTransitionSpec = { fondu() },
            entryProvider = entryProvider {
                entry<AccueilCle> {
                    val vm: AccueilViewModel = viewModel(factory = AccueilViewModel.fabrique(conteneur.accueil))
                    AccueilRoute(viewModel = vm, actions = actionsAccueil, margeBas = margeSos)
                }
                entry<MessagesCle> {
                    OngletAVenir(
                        destination = destinations[1],
                        message = "Ta messagerie chiffrée avec ${conteneur.prenomPartenaire} arrive bientôt.",
                        margeBas = margeSos,
                    )
                }
                entry<CycleCle> {
                    OngletAVenir(
                        destination = destinations[2],
                        message = "Le suivi détaillé de ton cycle arrive bientôt.",
                        margeBas = margeSos,
                    )
                }
                entry<NotesCle> {
                    OngletAVenir(
                        destination = destinations[3],
                        message = "Vos notes partagées arrivent bientôt.",
                        margeBas = margeSos,
                    )
                }
                entry<CalendrierCle> {
                    OngletAVenir(
                        destination = destinations[4],
                        message = "Votre calendrier commun arrive bientôt.",
                        margeBas = margeSos,
                    )
                }
            },
        )
    }
}
