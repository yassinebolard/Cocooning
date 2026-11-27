package fr.cocooning.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.icons.CocoIcons

/**
 * Champ de saisie : libellé visible au-dessus, bordure `contour` de 1,5 dp (2 dp au focus),
 * aide ou erreur dessous. Hauteur minimale 56 dp, aucune hauteur fixe.
 *
 * @param libelle toujours visible, jamais remplacé par un simple texte indicatif.
 * @param erreur message qui dit quoi corriger (« Numéro incomplet : il manque 4 chiffres. »).
 *   L'appelant ne le fournit qu'après que l'utilisateur a quitté le champ.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CocoTextField(
    valeur: String,
    onValeurChange: (String) -> Unit,
    libelle: String,
    modifier: Modifier = Modifier,
    aide: String? = null,
    erreur: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interactions = remember { MutableInteractionSource() }
    val enErreur = erreur != null
    val scheme = MaterialTheme.colorScheme
    val couleurs = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = scheme.outline,
        errorBorderColor = scheme.error,
        focusedContainerColor = CocooningTheme.cocoon.surface,
        unfocusedContainerColor = CocooningTheme.cocoon.surface,
        errorContainerColor = CocooningTheme.cocoon.surface,
        cursorColor = scheme.primary,
    )
    val texteSous = erreur ?: aide

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.Space1)) {
        // Le libellé est lu par TalkBack via le champ lui-même (voir semantics plus bas).
        Text(
            text = libelle,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurface,
            modifier = Modifier.clearAndSetSemantics { },
        )
        BasicTextField(
            value = valeur,
            onValueChange = onValeurChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.ComfortTarget)
                .semantics {
                    contentDescription = libelle
                    if (erreur != null) error(erreur)
                },
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(if (enErreur) scheme.error else scheme.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            interactionSource = interactions,
            decorationBox = { champ ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = valeur,
                    innerTextField = champ,
                    enabled = enabled,
                    singleLine = singleLine,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactions,
                    isError = enErreur,
                    trailingIcon = if (enErreur) {
                        { Icon(CocoIcons.Alerte, contentDescription = null, tint = scheme.error) }
                    } else {
                        null
                    },
                    colors = couleurs,
                    contentPadding = PaddingValues(horizontal = Dimens.Space4, vertical = Dimens.Space4),
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = enabled,
                            isError = enErreur,
                            interactionSource = interactions,
                            colors = couleurs,
                            shape = MaterialTheme.shapes.medium,
                            focusedBorderThickness = 2.dp,
                            unfocusedBorderThickness = Dimens.Filet,
                        )
                    },
                )
            },
        )
        if (texteSous != null) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space1),
                modifier = Modifier.padding(horizontal = Dimens.Space1),
            ) {
                if (enErreur) {
                    Icon(
                        CocoIcons.Alerte,
                        contentDescription = null,
                        tint = scheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = texteSous,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enErreur) scheme.error else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@CocoPreviews
@Composable
private fun CocoTextFieldPreview() {
    CocooningTheme {
        Column(
            modifier = Modifier.padding(Dimens.Space5),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space4),
        ) {
            var nom by remember { mutableStateOf("Mymy") }
            CocoTextField(
                valeur = nom,
                onValeurChange = { nom = it },
                libelle = "Prénom de ta moitié",
                aide = "Il s'affiche dans les alertes S.O.S.",
            )
            CocoTextField(
                valeur = "06 12 34",
                onValeurChange = {},
                libelle = "Numéro de secours",
                erreur = "Numéro incomplet : il manque 4 chiffres.",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
        }
    }
}
