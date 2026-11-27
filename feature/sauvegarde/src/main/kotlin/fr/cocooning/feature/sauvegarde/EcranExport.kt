package fr.cocooning.feature.sauvegarde

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.components.CocoButton
import fr.cocooning.designsystem.components.ErrorState
import fr.cocooning.designsystem.icons.CocoIcons
import java.time.LocalDate

/**
 * Création de la phrase de récupération et export du fichier de sauvegarde, branché à son
 * [ExportViewModel]. Le fichier est rangé par l'utilisateur via le sélecteur d'Android.
 */
@Composable
fun ExportRoute(
    viewModel: ExportViewModel,
    onTermine: () -> Unit,
    onRetour: () -> Unit,
    modifier: Modifier = Modifier,
    margeBas: PaddingValues = PaddingValues(),
) {
    val formulaire by viewModel.formulaire.collectAsStateWithLifecycle()
    val choixFichier = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportViewModel.TYPE_MIME),
    ) { uri -> viewModel.exporterVers(uri?.toString()) }
    // Pas de retour pendant le chiffrement : l'écriture irait au bout sans personne pour la voir.
    BackHandler(enabled = formulaire.etape == EtapeExport.Ecriture) {}
    EcranExport(
        formulaire = formulaire,
        actions = ActionsExport(
            onPhrase = viewModel::changerPhrase,
            onConfirmation = viewModel::changerConfirmation,
            onAfficher = viewModel::basculerAffichage,
            onCompris = viewModel::indiquerCompris,
            onExporter = {
                if (viewModel.pretPourChoisirFichier()) choixFichier.launch(formulaire.nomFichierPropose)
            },
            onTermine = onTermine,
            onRetour = onRetour,
        ),
        modifier = modifier,
        margeBas = margeBas,
    )
}

data class ActionsExport(
    val onPhrase: (String) -> Unit = {},
    val onConfirmation: (String) -> Unit = {},
    val onAfficher: () -> Unit = {},
    val onCompris: (Boolean) -> Unit = {},
    val onExporter: () -> Unit = {},
    val onTermine: () -> Unit = {},
    val onRetour: () -> Unit = {},
)

@Composable
fun EcranExport(
    formulaire: FormulaireExport,
    actions: ActionsExport,
    modifier: Modifier = Modifier,
    margeBas: PaddingValues = PaddingValues(),
) {
    val f = formulaire
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(margeBas)
            .padding(horizontal = Dimens.Space5, vertical = Dimens.Space6),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space4),
    ) {
        if (f.etape == EtapeExport.Termine) {
            Reussite(
                titre = "Sauvegarde enregistrée",
                texte = "Range ce fichier en dehors de ce téléphone : ordinateur, clé USB ou Drive. " +
                    "Avec ta phrase, il permet de tout retrouver sur un nouveau téléphone.",
            )
            Paragraphe(
                "Pense à refaire une sauvegarde de temps en temps : ce qui sera saisi après " +
                    "aujourd'hui n'est pas dans ce fichier. Cocooning te le rappellera.",
            )
            CocoButton(texte = "Revenir à l'accueil", onClick = actions.onTermine, modifier = Modifier.fillMaxWidth())
            return@Column
        }

        val occupe = f.etape == EtapeExport.Ecriture
        EnTeteSauvegarde(
            titre = "Sauvegarder mes données",
            sousTitre = "Pour les retrouver sur un nouveau téléphone. Le fichier est chiffré par une " +
                "phrase que tu es la seule personne à connaître.",
            onRetour = actions.onRetour.takeUnless { occupe },
            descriptionRetour = "Fermer sans sauvegarder",
        )
        f.dernierExport?.let {
            Paragraphe("Dernière sauvegarde : le ${dateLongue(it)}. Tu peux reprendre la même phrase ou en choisir une nouvelle.")
        }

        Avertissement(
            titre = "Phrase oubliée, sauvegarde perdue",
            texte = "Personne ne pourra ouvrir ce fichier sans ta phrase : ni toi, ni ta moitié, ni " +
                "Cocooning. Il n'existe aucun moyen de la récupérer. Écris-la sur papier et range-la " +
                "en lieu sûr.",
            icone = CocoIcons.Cadenas,
        )

        ChampPhrase(
            valeur = f.phrase,
            onValeurChange = actions.onPhrase,
            libelle = "Phrase de récupération",
            afficher = f.afficher,
            enabled = !occupe,
        )
        IndicateurRobustesse(phrase = f.phrase, robustesse = f.robustesse)
        Paragraphe(
            "Choisis-la difficile à deviner, même pour un proche : quelqu'un qui obtiendrait le " +
                "fichier pourrait essayer des phrases sans limite. Les majuscules et les espaces en " +
                "trop ne comptent pas.",
        )

        ChampPhrase(
            valeur = f.confirmation,
            onValeurChange = actions.onConfirmation,
            libelle = "La même phrase, encore une fois",
            afficher = f.afficher,
            erreur = f.erreurConfirmation,
            enabled = !occupe,
            imeAction = ImeAction.Done,
        )
        BoutonAffichage(afficher = f.afficher, onBasculer = actions.onAfficher, enabled = !occupe)

        CaseACocher(
            texte = "J'ai écrit ma phrase et je sais que, sans elle, personne ne pourra ouvrir la sauvegarde.",
            coche = f.compris,
            onChange = actions.onCompris,
            enabled = !occupe,
        )

        if (f.manques.isNotEmpty()) {
            ErrorState(message = "Il manque encore " + enumeration(f.manques) + ".")
        }
        f.erreur?.let { ErrorState(message = it) }

        CocoButton(
            texte = "Choisir où enregistrer le fichier",
            onClick = actions.onExporter,
            icone = CocoIcons.Fichier,
            chargement = occupe,
            texteChargement = "Chiffrement en cours, quelques secondes…",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** « a », « a et b », « a, b et c ». */
internal fun enumeration(elements: List<String>): String =
    if (elements.size <= 1) elements.joinToString() else elements.dropLast(1).joinToString(", ") + " et " + elements.last()

private val MOIS = listOf(
    "janvier", "février", "mars", "avril", "mai", "juin",
    "juillet", "août", "septembre", "octobre", "novembre", "décembre",
)

/** « 27 novembre 2026 ». */
internal fun dateLongue(jour: LocalDate): String =
    "${if (jour.dayOfMonth == 1) "1er" else jour.dayOfMonth} ${MOIS[jour.monthValue - 1]} ${jour.year}"

@CocoPreviews
@Composable
private fun EcranExportPreview() {
    CocooningTheme {
        EcranExport(
            formulaire = FormulaireExport(
                phrase = "parapluie violet mange trois",
                confirmation = "parapluie violet",
                afficher = true,
            ),
            actions = ActionsExport(),
        )
    }
}

@CocoPreviews
@Composable
private fun EcranExportTerminePreview() {
    CocooningTheme {
        EcranExport(formulaire = FormulaireExport(etape = EtapeExport.Termine), actions = ActionsExport())
    }
}

