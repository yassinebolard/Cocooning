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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.components.CocoButton
import fr.cocooning.designsystem.components.CocoButtonStyle
import fr.cocooning.designsystem.components.CocoCard
import fr.cocooning.designsystem.components.ErrorState
import fr.cocooning.designsystem.icons.CocoIcons

/**
 * Restauration d'une sauvegarde au premier lancement, branchée à son [RestaurationViewModel].
 *
 * @param onRetour revient à l'accueil du premier lancement.
 * @param onRedemarrer redémarre l'appli pour que les bases restaurées soient rouvertes.
 */
@Composable
fun RestaurationRoute(
    viewModel: RestaurationViewModel,
    onRetour: () -> Unit,
    onRedemarrer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formulaire by viewModel.formulaire.collectAsStateWithLifecycle()
    // Tout type de fichier : selon l'endroit où il a été rangé, le type annoncé varie.
    val choixFichier = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.fichierChoisi(uri?.toString())
    }
    BackHandler(enabled = formulaire.etape.occupe) {}
    BackHandler(enabled = !formulaire.etape.occupe && formulaire.etape != EtapeRestauration.Termine, onBack = onRetour)
    EcranRestauration(
        formulaire = formulaire,
        actions = ActionsRestauration(
            onChoisirFichier = { choixFichier.launch(arrayOf("*/*")) },
            onPhrase = viewModel::changerPhrase,
            onAfficher = viewModel::basculerAffichage,
            onRestaurer = viewModel::restaurer,
            onChangerDeFichier = viewModel::changerDeFichier,
            onRetour = onRetour,
            onRedemarrer = onRedemarrer,
        ),
        modifier = modifier,
    )
}

data class ActionsRestauration(
    val onChoisirFichier: () -> Unit = {},
    val onPhrase: (String) -> Unit = {},
    val onAfficher: () -> Unit = {},
    val onRestaurer: () -> Unit = {},
    val onChangerDeFichier: () -> Unit = {},
    val onRetour: () -> Unit = {},
    val onRedemarrer: () -> Unit = {},
)

@Composable
fun EcranRestauration(
    formulaire: FormulaireRestauration,
    actions: ActionsRestauration,
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
        if (f.etape == EtapeRestauration.Termine) {
            Reussite(
                titre = "Tes données sont de retour",
                texte = "Cocooning doit redémarrer pour les rouvrir. Cela prend une seconde.",
            )
            CocoButton(texte = "Redémarrer Cocooning", onClick = actions.onRedemarrer, modifier = Modifier.fillMaxWidth())
            return@Column
        }

        val occupe = f.etape.occupe
        EnTeteSauvegarde(
            titre = "Restaurer une sauvegarde",
            sousTitre = "Choisis le fichier exporté depuis ton ancien téléphone, puis tape la phrase " +
                "de récupération choisie ce jour-là.",
            onRetour = actions.onRetour.takeUnless { occupe },
            descriptionRetour = "Revenir sans restaurer",
        )

        val fichierPret = f.etape == EtapeRestauration.Saisie ||
            f.etape == EtapeRestauration.Verification ||
            f.etape == EtapeRestauration.Restauration

        if (!fichierPret) {
            f.erreurFichier?.let { ErrorState(message = it) }
            CocoButton(
                texte = "Choisir le fichier",
                onClick = actions.onChoisirFichier,
                icone = CocoIcons.Fichier,
                chargement = f.etape == EtapeRestauration.LectureFichier,
                texteChargement = "Lecture du fichier…",
                modifier = Modifier.fillMaxWidth(),
            )
            Paragraphe(
                "Son nom ressemble à « cocooning-sauvegarde-2026-11-27.${ExportViewModel.EXTENSION} ». " +
                    "S'il est sur un ordinateur, copie-le d'abord sur ce téléphone ou dans ton Drive.",
            )
            return@Column
        }

        CocoCard(titre = "Fichier choisi", icone = CocoIcons.Fichier, tonale = true, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = f.nomFichier ?: "Sauvegarde Cocooning",
                style = MaterialTheme.typography.bodyLarge,
            )
            CocoButton(
                texte = "Choisir un autre fichier",
                onClick = actions.onChangerDeFichier,
                style = CocoButtonStyle.Texte,
                enabled = !occupe,
            )
        }

        ChampPhrase(
            valeur = f.phrase,
            onValeurChange = actions.onPhrase,
            libelle = "Phrase de récupération",
            afficher = f.afficher,
            aide = "Les majuscules et les espaces en trop ne comptent pas.",
            erreur = f.erreurPhrase,
            enabled = !occupe,
            imeAction = ImeAction.Done,
            onValider = actions.onRestaurer,
        )
        BoutonAffichage(afficher = f.afficher, onBasculer = actions.onAfficher, enabled = !occupe)

        f.erreur?.let { ErrorState(message = it) }

        CocoButton(
            texte = "Restaurer",
            onClick = actions.onRestaurer,
            chargement = occupe,
            texteChargement = if (f.etape == EtapeRestauration.Restauration) {
                "Remise en place des données…"
            } else {
                "Vérification de la phrase, quelques secondes…"
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Paragraphe("Rien n'est modifié tant que la phrase n'est pas vérifiée.")
    }
}

@CocoPreviews
@Composable
private fun EcranRestaurationPreview() {
    CocooningTheme {
        EcranRestauration(
            formulaire = FormulaireRestauration(
                etape = EtapeRestauration.Saisie,
                nomFichier = "cocooning-sauvegarde-2026-11-27.cocooning",
                phrase = "parapluie violet",
                afficher = true,
                erreurPhrase = RestaurationViewModel.PHRASE_INCORRECTE,
            ),
            actions = ActionsRestauration(),
        )
    }
}

@CocoPreviews
@Composable
private fun EcranRestaurationChoixPreview() {
    CocooningTheme {
        EcranRestauration(
            formulaire = FormulaireRestauration(erreurFichier = RestaurationViewModel.PAS_UNE_SAUVEGARDE),
            actions = ActionsRestauration(),
        )
    }
}
