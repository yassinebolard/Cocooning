package fr.cocooning.feature.sauvegarde

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.cocooning.core.securite.sauvegarde.PhraseRecuperation
import fr.cocooning.core.securite.sauvegarde.PhraseRecuperation.Niveau
import fr.cocooning.designsystem.CocoShapes
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.components.CocoButton
import fr.cocooning.designsystem.components.CocoButtonStyle
import fr.cocooning.designsystem.components.CocoIconButton
import fr.cocooning.designsystem.components.CocoTextField
import fr.cocooning.designsystem.icons.CocoIcons

/** Titre d'écran, lu comme un titre par TalkBack, avec un bouton de fermeture facultatif. */
@Composable
internal fun EnTeteSauvegarde(
    titre: String,
    sousTitre: String? = null,
    onRetour: (() -> Unit)? = null,
    descriptionRetour: String = "Fermer",
) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)) {
        if (onRetour != null) {
            CocoIconButton(icone = CocoIcons.Fermer, contentDescription = descriptionRetour, onClick = onRetour)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.Space1)) {
            Text(
                text = titre,
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            if (sousTitre != null) {
                Text(text = sousTitre, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Encadré d'avertissement : fond `vigilance-douce`, icône et texte (la couleur ne porte jamais
 * seule le message). Le titre et le texte sont lus d'un bloc par TalkBack.
 */
@Composable
internal fun Avertissement(titre: String, texte: String, modifier: Modifier = Modifier, icone: ImageVector = CocoIcons.Alerte) {
    val cocoon = CocooningTheme.cocoon
    Surface(
        color = cocoon.vigilanceDouce,
        contentColor = cocoon.onVigilanceDouce,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(Dimens.Space4),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space3),
        ) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(Dimens.IconSize))
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space1)) {
                Text(titre, style = MaterialTheme.typography.titleMedium)
                Text(texte, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Champ de phrase de récupération : masqué par défaut, clavier sans suggestions ni majuscule
 * automatique (un clavier qui apprend la phrase la garderait ailleurs que dans la tête).
 */
@Composable
internal fun ChampPhrase(
    valeur: String,
    onValeurChange: (String) -> Unit,
    libelle: String,
    afficher: Boolean,
    modifier: Modifier = Modifier,
    aide: String? = null,
    erreur: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onValider: () -> Unit = {},
) {
    CocoTextField(
        valeur = valeur,
        // Plusieurs lignes pour relire une longue phrase, mais jamais de retour à la ligne tapé.
        onValeurChange = { onValeurChange(it.replace('\n', ' ')) },
        libelle = libelle,
        aide = aide,
        erreur = erreur,
        enabled = enabled,
        singleLine = false,
        masque = !afficher,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onValider() }),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Bouton qui affiche ou masque les phrases de l'écran. */
@Composable
internal fun BoutonAffichage(afficher: Boolean, onBasculer: () -> Unit, enabled: Boolean = true) {
    CocoButton(
        texte = if (afficher) "Masquer la phrase" else "Afficher la phrase",
        onClick = onBasculer,
        style = CocoButtonStyle.Texte,
        enabled = enabled,
    )
}

/** Case à cocher dont toute la ligne est cible (48 dp au moins), lue comme une case par TalkBack. */
@Composable
internal fun CaseACocher(texte: String, coche: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MinTouchTarget)
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = coche, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = Dimens.Space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space3),
    ) {
        Checkbox(
            checked = coche,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
        )
        Text(texte, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/**
 * Indicateur de robustesse : trois segments, un libellé et un conseil. La couleur double le
 * texte, elle ne le remplace pas. Seul le changement de niveau est annoncé par TalkBack, pas
 * chaque caractère tapé.
 */
@Composable
internal fun IndicateurRobustesse(phrase: String, robustesse: PhraseRecuperation.Robustesse, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val cocoon = CocooningTheme.cocoon
    val vide = phrase.isBlank()
    val segments = when {
        vide -> 0
        robustesse.niveau == Niveau.Solide -> 3
        robustesse.niveau == Niveau.Correcte -> 2
        else -> 1
    }
    val couleur = when (robustesse.niveau) {
        Niveau.Solide -> cocoon.succes
        Niveau.Correcte -> cocoon.vigilance
        else -> scheme.error
    }
    val libelle = when {
        vide -> "à choisir"
        robustesse.niveau == Niveau.TropCourte -> "trop courte"
        robustesse.niveau == Niveau.Faible -> "trop facile à deviner"
        robustesse.niveau == Niveau.Correcte -> "correcte"
        else -> "solide"
    }
    val conseil = when {
        vide -> "Au moins ${PhraseRecuperation.LONGUEUR_MINIMALE} caractères. Le plus simple : quatre ou " +
            "cinq mots sans lien entre eux."
        robustesse.niveau == Niveau.TropCourte -> {
            val reste = PhraseRecuperation.LONGUEUR_MINIMALE - robustesse.longueur
            if (reste == 1) "Encore 1 caractère au minimum." else "Encore $reste caractères au minimum."
        }
        robustesse.niveau == Niveau.Faible -> "Évite les suites connues et les répétitions. Ajoute des mots sans lien entre eux."
        robustesse.niveau == Niveau.Correcte -> "Elle convient. Un ou deux mots de plus la rendraient solide."
        else -> "Elle protège bien ta sauvegarde."
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.Space2)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space1),
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CocoShapes.Pilule)
                        .background(if (i < segments) couleur else scheme.surfaceContainerHighest),
                )
            }
        }
        Text(
            text = "Robustesse : $libelle",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(text = conseil, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    }
}

/** Texte de corps courant. */
@Composable
internal fun Paragraphe(texte: String, modifier: Modifier = Modifier) {
    Text(texte, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = modifier)
}

/** Bandeau de réussite : icône dans un disque `principale-douce`, titre, texte. */
@Composable
internal fun Reussite(titre: String, texte: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Dimens.Space3),
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.SosTarget)
                .background(scheme.primaryContainer, CocoShapes.Pilule),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CocoIcons.Coche, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
        }
        Text(
            titre,
            style = MaterialTheme.typography.headlineMedium,
            color = scheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(texte, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
    }
}
