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
import fr.cocooning.feature.cycle.ActionsCycle
import fr.cocooning.feature.cycle.AjoutReglesCle
import fr.cocooning.feature.cycle.ConfigurationRoute
import fr.cocooning.feature.cycle.ConfigurationViewModel
import fr.cocooning.feature.cycle.CorrectionCycleCle
import fr.cocooning.feature.cycle.CorrectionRoute
import fr.cocooning.feature.cycle.CorrectionViewModel
import fr.cocooning.feature.cycle.CycleCle
import fr.cocooning.feature.cycle.CycleRoute
import fr.cocooning.feature.cycle.CycleViewModel
import fr.cocooning.feature.cycle.ReglagesCycleCle
import fr.cocooning.feature.messages.MessagesRoute
import fr.cocooning.feature.messages.MessagesViewModel
import fr.cocooning.feature.sauvegarde.ExportRoute
import fr.cocooning.feature.sauvegarde.ExportViewModel
import fr.cocooning.feature.sauvegarde.LienSauvegarde
import fr.cocooning.feature.sauvegarde.RappelSauvegardeRoute
import fr.cocooning.feature.sauvegarde.RappelViewModel
import fr.cocooning.feature.sauvegarde.SauvegardeCle
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

    val dependancesSauvegarde = conteneur.dependancesSauvegarde
    val ouvrirSauvegarde = { navigation.ouvrir(SauvegardeCle) }

    val dependancesCycle = conteneur.dependancesCycle
    val actionsCycle = ActionsCycle(
        onCorriger = { id -> navigation.ouvrir(CorrectionCycleCle(id)) },
        onAjouter = { navigation.ouvrir(AjoutReglesCle) },
        onReglages = { navigation.ouvrir(ReglagesCycleCle) },
    )
    val terminerCycle: (String) -> Unit = { annonce ->
        annoncer(annonce)
        navigation.retour()
    }

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
                    AccueilRoute(
                        viewModel = vm,
                        actions = actionsAccueil,
                        margeBas = margeSos,
                        rappel = {
                            val rappel: RappelViewModel = viewModel(factory = RappelViewModel.fabrique(dependancesSauvegarde))
                            RappelSauvegardeRoute(viewModel = rappel, onSauvegarder = ouvrirSauvegarde)
                        },
                        piedDePage = { LienSauvegarde(onSauvegarder = ouvrirSauvegarde) },
                    )
                }
                entry<SauvegardeCle> {
                    val vm: ExportViewModel = viewModel(factory = ExportViewModel.fabrique(dependancesSauvegarde))
                    ExportRoute(
                        viewModel = vm,
                        onTermine = { navigation.retour() },
                        onRetour = { navigation.retour() },
                        margeBas = margeSos,
                    )
                }
                entry<MessagesCle> {
                    val vm: MessagesViewModel = viewModel(factory = MessagesViewModel.fabrique(conteneur.messagerie))
                    MessagesRoute(
                        viewModel = vm,
                        prenomPartenaire = conteneur.prenomPartenaire,
                        onAnnoncer = annoncer,
                        margeBas = margeSos,
                    )
                }
                entry<CycleCle> {
                    val vm: CycleViewModel = viewModel(factory = CycleViewModel.fabrique(dependancesCycle))
                    CycleRoute(
                        viewModel = vm,
                        configuration = { viewModel(factory = ConfigurationViewModel.fabrique(dependancesCycle)) },
                        actions = actionsCycle,
                        margeBas = margeSos,
                    )
                }
                entry<CorrectionCycleCle> { cle ->
                    val vm: CorrectionViewModel =
                        viewModel(factory = CorrectionViewModel.fabrique(dependancesCycle, cle.cycleId))
                    CorrectionRoute(
                        viewModel = vm,
                        onTermine = terminerCycle,
                        onRetour = { navigation.retour() },
                        margeBas = margeSos,
                    )
                }
                entry<AjoutReglesCle> {
                    val vm: CorrectionViewModel = viewModel(factory = CorrectionViewModel.fabrique(dependancesCycle, null))
                    CorrectionRoute(
                        viewModel = vm,
                        onTermine = terminerCycle,
                        onRetour = { navigation.retour() },
                        margeBas = margeSos,
                    )
                }
                entry<ReglagesCycleCle> {
                    val vm: ConfigurationViewModel = viewModel(factory = ConfigurationViewModel.fabrique(dependancesCycle))
                    ConfigurationRoute(
                        viewModel = vm,
                        onTermine = { terminerCycle("C'est enregistré. Tes prévisions sont à jour.") },
                        onRetour = { navigation.retour() },
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
