package fr.cocooning.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.icons.CocoIcons

/**
 * Zone de saisie de la messagerie : bouton « + » (48 dp), zone `carte` arrondie qui grandit
 * jusqu'à 5 lignes, puis le micro quand le champ est vide ou le bouton Envoyer quand il
 * contient du texte. Chaque bouton fait 48 dp et a une description pour TalkBack.
 *
 * Le texte indicatif ne remplace pas le libellé : la zone est décrite « Message » pour
 * TalkBack, et le texte indicatif disparaît dès la première lettre.
 */
@Composable
fun MessageComposer(
    valeur: String,
    onValeurChange: (String) -> Unit,
    onEnvoyer: () -> Unit,
    onJoindre: () -> Unit,
    onMicro: () -> Unit,
    modifier: Modifier = Modifier,
    texteIndicatif: String = "Écrire un message",
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.Space2, vertical = Dimens.Space2),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space1),
        verticalAlignment = Alignment.Bottom,
    ) {
        CocoIconButton(
            icone = CocoIcons.Plus,
            contentDescription = "Ajouter une pièce jointe",
            onClick = onJoindre,
        )
        BasicTextField(
            value = valeur,
            onValueChange = onValeurChange,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Message" },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            maxLines = 5,
            decorationBox = { champ ->
                Box(
                    modifier = Modifier
                        .heightIn(min = Dimens.MinTouchTarget)
                        .background(scheme.surfaceContainer, MaterialTheme.shapes.extraLarge)
                        .padding(horizontal = Dimens.Space4, vertical = Dimens.Space3),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (valeur.isEmpty()) {
                        Text(
                            texteIndicatif,
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    champ()
                }
            },
        )
        if (valeur.isBlank()) {
            CocoIconButton(
                icone = CocoIcons.Micro,
                contentDescription = "Enregistrer un message vocal",
                onClick = onMicro,
            )
        } else {
            FilledIconButton(onClick = onEnvoyer, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
                Icon(CocoIcons.Envoyer, contentDescription = "Envoyer le message")
            }
        }
    }
}

@CocoPreviews
@Composable
private fun MessageComposerPreview() {
    CocooningTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space3)) {
            var vide by remember { mutableStateOf("") }
            var plein by remember { mutableStateOf("Je passe prendre du pain en rentrant, tu veux autre chose ?") }
            MessageComposer(vide, { vide = it }, onEnvoyer = {}, onJoindre = {}, onMicro = {})
            MessageComposer(plein, { plein = it }, onEnvoyer = {}, onJoindre = {}, onMicro = {})
        }
    }
}
