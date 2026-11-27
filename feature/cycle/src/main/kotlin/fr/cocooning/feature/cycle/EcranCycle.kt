package fr.cocooning.feature.cycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cocooning.core.cycle.domaine.Bilan
import fr.cocooning.core.cycle.domaine.CalculateurCycle
import fr.cocooning.core.cycle.domaine.CycleSaisi
import fr.cocooning.core.cycle.domaine.EtatCycle
import fr.cocooning.core.cycle.domaine.ParametresCycle
import fr.cocooning.core.cycle.domaine.Phase
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.components.CocoButton
import fr.cocooning.designsystem.components.CocoButtonStyle
import fr.cocooning.designsystem.components.CocoCard
import fr.cocooning.designsystem.components.ErrorState
import fr.cocooning.designsystem.components.LoadingState
import fr.cocooning.designsystem.components.PhaseCard
import fr.cocooning.designsystem.components.PhaseCycle
import fr.cocooning.designsystem.icons.CocoIcons
import java.time.LocalDate

/** Où mènent les actions de l'onglet Cycle. L'application les relie à sa pile de navigation. */
data class ActionsCycle(
    val onCorriger: (cycleId: Long) -> Unit = {},
    val onAjouter: () -> Unit = {},
    val onReglages: () -> Unit = {},
)

/**
 * Onglet Cycle branché à ses ViewModel. Tant que madame n'a pas fait la saisie du premier
 * lancement (EF-CYC-02), l'onglet la propose à la place de l'historique.
 *
 * @param margeBas place laissée sous le contenu pour le bouton S.O.S flottant.
 */
@Composable
fun CycleRoute(
    viewModel: CycleViewModel,
    configuration: @Composable () -> ConfigurationViewModel,
    actions: ActionsCycle,
    modifier: Modifier = Modifier,
    margeBas: PaddingValues = PaddingValues(),
) {
    val etat by viewModel.etat.collectAsStateWithLifecycle()
    if (etat is CycleUiState.PremiereSaisie) {
        ConfigurationRoute(viewModel = configuration(), onTermine = {}, modifier = modifier, margeBas = margeBas)
    } else {
        EcranCycle(etat = etat, actions = actions, modifier = modifier, margeBas = margeBas)
    }
}

/** Onglet Cycle sans état : tout ce qu'il affiche vient de [etat]. */
@Composable
fun EcranCycle(
    etat: CycleUiState,
    actions: ActionsCycle,
    modifier: Modifier = Modifier,
    margeBas: PaddingValues = PaddingValues(),
) {
    when (etat) {
        CycleUiState.Chargement, CycleUiState.PremiereSaisie -> LoadingState(modifier.padding(Dimens.Space5))
        is CycleUiState.Erreur -> ErrorState(message = etat.message, modifier = modifier.padding(Dimens.Space5))
        is CycleUiState.Pret -> ContenuCycle(etat, actions, modifier, margeBas)
    }
}

@Composable
private fun ContenuCycle(etat: CycleUiState.Pret, actions: ActionsCycle, modifier: Modifier, margeBas: PaddingValues) {
    val scheme = MaterialTheme.colorScheme
    val aujourdhui = etat.aujourdhui
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(margeBas)
            .padding(horizontal = Dimens.Space5, vertical = Dimens.Space6),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space3),
    ) {
        EnTeteCycle(titre = "Mon cycle", sousTitre = "Visible seulement sur ton téléphone")

        when (val cycle = etat.etat) {
            EtatCycle.SansDonnees -> CocoCard(titre = "Aucunes règles saisies", icone = CocoIcons.Goutte, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Touche « Mes règles ont commencé » sur l'accueil le premier jour de tes règles, " +
                        "ou ajoute ici des règles passées.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is EtatCycle.Suivi -> PhaseCard(
                phase = cycle.phase.versDesign(),
                titre = "${cycle.phase.versDesign().nom}, jour ${cycle.jour}",
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(texteEtat(cycle, aujourdhui), style = MaterialTheme.typography.bodyMedium)
            }
        }

        CocoCard(titre = "Mes durées", icone = CocoIcons.Cycle, modifier = Modifier.fillMaxWidth()) {
            Text("Cycle : ${texteMoyenneCycle(etat.bilan.dureeCycle)}.", style = MaterialTheme.typography.bodyMedium)
            Text("Règles : ${texteMoyenneRegles(etat.bilan.dureeRegles)}.", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Elles se recalculent à chaque correction, sur tes ${CalculateurCycle.FENETRE} derniers cycles au plus.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            val contraception = etat.parametres.contraception
            if (contraception?.hormonale == true) {
                Text(
                    "Avec ta contraception (${libelle(contraception).lowercase()}), " +
                        "les phases affichées sont indicatives.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        TitreSection("Mes règles passées")
        if (etat.bilan.cycles.isNotEmpty()) {
            TexteSousChamp("Touche des règles pour corriger leurs dates ou les supprimer.")
        }
        etat.bilan.cycles.forEach { passe ->
            CocoCard(
                titre = titreCycle(passe, aujourdhui),
                icone = CocoIcons.Goutte,
                onClick = { actions.onCorriger(passe.cycle.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                detailsCycle(passe).forEach { ligne ->
                    Text(ligne, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
        }

        CocoButton(
            texte = "Ajouter des règles oubliées",
            onClick = actions.onAjouter,
            style = CocoButtonStyle.Contour,
            icone = CocoIcons.Plus,
            modifier = Modifier.fillMaxWidth(),
        )
        CocoButton(
            texte = "Durées habituelles et contraception",
            onClick = actions.onReglages,
            style = CocoButtonStyle.Texte,
            modifier = Modifier.fillMaxWidth(),
        )
        TexteSousChamp(AVERTISSEMENT_MEDICAL)
    }
}

/** Phrase de la carte de phase. Toute prévision est dite estimée. */
internal fun texteEtat(cycle: EtatCycle.Suivi, aujourdhui: LocalDate): String = when {
    cycle.reglesEnCours -> "Règles en cours depuis ${jours(cycle.jour)}. Pense à indiquer quand elles se terminent."
    cycle.joursDeRetard > 0 ->
        "Règles attendues ${fourchette(cycle.prochainesRegles, aujourdhui)}, pas encore indiquées."
    cycle.finOubliee ->
        "Fin des règles non indiquée, estimée à ${jours(cycle.dureeRegles)}. " +
            "Prochaines règles estimées ${fourchette(cycle.prochainesRegles, aujourdhui)}."
    else -> "Prochaines règles estimées ${fourchette(cycle.prochainesRegles, aujourdhui)}."
}

internal fun Phase.versDesign(): PhaseCycle = when (this) {
    Phase.Menstruelle -> PhaseCycle.Menstruelle
    Phase.Folliculaire -> PhaseCycle.Folliculaire
    Phase.Ovulatoire -> PhaseCycle.Ovulatoire
    Phase.Luteale -> PhaseCycle.Luteale
}

@CocoPreviews
@Composable
private fun EcranCyclePreview() {
    val aujourdhui = LocalDate.of(2026, 11, 27)
    val historique = listOf(
        CycleSaisi(1, LocalDate.of(2026, 8, 30), LocalDate.of(2026, 9, 3)),
        CycleSaisi(2, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 2)),
        CycleSaisi(3, LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 31)),
        CycleSaisi(4, LocalDate.of(2026, 11, 25)),
    )
    val parametres = ParametresCycle()
    CocooningTheme {
        EcranCycle(
            etat = CycleUiState.Pret(
                aujourdhui = aujourdhui,
                etat = CalculateurCycle.etat(historique, parametres, aujourdhui),
                bilan = Bilan.calculer(historique, parametres, aujourdhui),
                parametres = parametres,
            ),
            actions = ActionsCycle(),
        )
    }
}
