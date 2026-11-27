package fr.cocooning.feature.sauvegarde

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.cocooning.designsystem.CocoPreviews
import fr.cocooning.designsystem.CocooningTheme
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.components.CocoButton
import fr.cocooning.designsystem.components.CocoButtonStyle
import fr.cocooning.designsystem.icons.CocoIcons

/**
 * Premier lancement : commencer à zéro, ou restaurer la sauvegarde d'un ancien téléphone.
 * N'apparaît qu'une fois, et jamais sur un téléphone qui a déjà des données.
 */
@Composable
fun EcranBienvenue(onCommencer: () -> Unit, onRestaurer: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.Space5, vertical = Dimens.Space8),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space4),
    ) {
        EnTeteSauvegarde(
            titre = "Bienvenue dans Cocooning",
            sousTitre = "Votre cocon à deux : messages, cycle, notes et calendrier, chiffrés sur vos téléphones.",
        )
        Avertissement(
            titre = "Tu changes de téléphone ?",
            texte = "Si tu as exporté une sauvegarde depuis l'ancien, restaure-la maintenant pour " +
                "retrouver ton historique. Ce choix n'est proposé qu'au premier lancement.",
            icone = CocoIcons.Cadenas,
        )
        CocoButton(texte = "Commencer", onClick = onCommencer, modifier = Modifier.fillMaxWidth())
        CocoButton(
            texte = "Restaurer une sauvegarde",
            onClick = onRestaurer,
            style = CocoButtonStyle.Contour,
            icone = CocoIcons.Fichier,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@CocoPreviews
@Composable
private fun EcranBienvenuePreview() {
    CocooningTheme { EcranBienvenue(onCommencer = {}, onRestaurer = {}) }
}
