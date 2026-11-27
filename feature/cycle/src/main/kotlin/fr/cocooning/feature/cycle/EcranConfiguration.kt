package fr.cocooning.feature.cycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cocooning.core.cycle.domaine.Contraception
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.components.CocoButton
import fr.cocooning.designsystem.components.CocoFilterChip
import fr.cocooning.designsystem.components.LoadingState
import java.time.LocalDate

/**
 * Saisie du premier lancement (EF-CYC-02), ou modification des réglages quand madame y revient.
 *
 * @param onTermine appelé une fois la saisie enregistrée.
 * @param onRetour bouton de fermeture, seulement quand l'écran est empilé (modification).
 */
@Composable
fun ConfigurationRoute(
    viewModel: ConfigurationViewModel,
    onTermine: () -> Unit,
    modifier: Modifier = Modifier,
    onRetour: (() -> Unit)? = null,
    margeBas: PaddingValues = PaddingValues(),
) {
    val formulaire by viewModel.formulaire.collectAsStateWithLifecycle()
    LaunchedEffect(formulaire.enregistre) { if (formulaire.enregistre) onTermine() }
    EcranConfiguration(
        formulaire = formulaire,
        actions = ActionsConfiguration(
            onDernieresRegles = viewModel::choisirDernieresRegles,
            onReglesEnCours = viewModel::indiquerReglesEnCours,
            onFin = viewModel::choisirFin,
            onDureeCycle = viewModel::changerDureeCycle,
            onDureeRegles = viewModel::changerDureeRegles,
            onContraception = viewModel::choisirContraception,
            onEnregistrer = viewModel::enregistrer,
            onRetour = onRetour,
        ),
        modifier = modifier,
        margeBas = margeBas,
    )
}

data class ActionsConfiguration(
    val onDernieresRegles: (LocalDate) -> Unit = {},
    val onReglesEnCours: (Boolean) -> Unit = {},
    val onFin: (LocalDate) -> Unit = {},
    val onDureeCycle: (Int) -> Unit = {},
    val onDureeRegles: (Int) -> Unit = {},
    val onContraception: (Contraception) -> Unit = {},
    val onEnregistrer: () -> Unit = {},
    val onRetour: (() -> Unit)? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EcranConfiguration(
    formulaire: FormulaireConfiguration,
    actions: ActionsConfiguration,
    modifier: Modifier = Modifier,
    margeBas: PaddingValues = PaddingValues(),
) {
    if (!formulaire.charge) {
        LoadingState(modifier.padding(Dimens.Space5))
        return
    }
    val f = formulaire
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(margeBas)
            .padding(horizontal = Dimens.Space5, vertical = Dimens.Space6),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space4),
    ) {
        if (f.modification) {
            EnTeteCycle(titre = "Durées habituelles", onRetour = actions.onRetour)
        } else {
            EnTeteCycle(
                titre = "Commençons ton suivi",
                sousTitre = "Quelques réponses pour des prévisions justes dès le premier cycle. " +
                    "Elles restent sur ton téléphone.",
                onRetour = actions.onRetour,
            )
        }

        if (f.demanderDate) {
            ChampDate(
                libelle = "Premier jour de tes dernières règles",
                date = f.dernieresRegles,
                aujourdhui = f.aujourdhui,
                onChoisir = actions.onDernieresRegles,
                aide = "Facultatif. Si tu ne t'en souviens pas, le suivi commencera à tes prochaines règles.",
                modifier = Modifier.fillMaxWidth(),
            )
            if (f.demanderFin) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space2)) {
                    CocoFilterChip(
                        libelle = "Elles sont encore en cours",
                        choisi = f.reglesEnCours,
                        onClick = { actions.onReglesEnCours(!f.reglesEnCours) },
                    )
                    if (!f.reglesEnCours) {
                        ChampDate(
                            libelle = "Dernier jour de ces règles",
                            date = f.finDernieresRegles,
                            aujourdhui = f.aujourdhui,
                            onChoisir = actions.onFin,
                            texteVide = "Je ne sais plus",
                            aide = "Facultatif. Sans date, la durée sera estimée.",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        CompteurJours(
            libelle = "Durée habituelle de ton cycle",
            valeur = f.dureeCycle,
            plage = f.plageCycle,
            onChange = actions.onDureeCycle,
            aide = "Du premier jour des règles au premier jour des suivantes. 28 jours si tu ne sais pas.",
        )
        CompteurJours(
            libelle = "Durée habituelle de tes règles",
            valeur = f.dureeRegles,
            plage = f.plageRegles,
            onChange = actions.onDureeRegles,
            aide = "5 jours si tu ne sais pas.",
        )

        Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space2)) {
            Text("Contraception", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space2),
            ) {
                Contraception.entries.forEach { choix ->
                    CocoFilterChip(
                        libelle = libelle(choix),
                        choisi = f.contraception == choix,
                        onClick = { actions.onContraception(choix) },
                    )
                }
            }
            TexteSousChamp("Facultatif. Elle n'est jamais partagée.")
        }

        f.erreur?.let { TexteSousChamp(it, erreur = true) }

        CocoButton(
            texte = if (f.modification) "Enregistrer" else "Commencer le suivi",
            onClick = actions.onEnregistrer,
            chargement = f.enregistrement,
            texteChargement = "Enregistrement…",
            modifier = Modifier.fillMaxWidth(),
        )
        TexteSousChamp(AVERTISSEMENT_MEDICAL)
    }
}

@CocoPreviews
@Composable
private fun EcranConfigurationPreview() {
    val aujourdhui = LocalDate.of(2026, 11, 27)
    CocooningTheme {
        EcranConfiguration(
            formulaire = FormulaireConfiguration(
                charge = true,
                aujourdhui = aujourdhui,
                dernieresRegles = aujourdhui.minusDays(3),
                contraception = Contraception.SteriletCuivre,
            ),
            actions = ActionsConfiguration(),
        )
    }
}
