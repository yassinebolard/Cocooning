package fr.cocooning.feature.cycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import fr.cocooning.designsystem.Dimens
import fr.cocooning.designsystem.components.CocoButton
import fr.cocooning.designsystem.components.CocoButtonStyle
import fr.cocooning.designsystem.components.CocoIconButton
import fr.cocooning.designsystem.icons.CocoIcons
import java.time.LocalDate
import java.time.ZoneOffset

/** Titre d'un écran de l'onglet Cycle, avec un bouton pour revenir quand [onRetour] est fourni. */
@Composable
internal fun EnTeteCycle(titre: String, sousTitre: String? = null, onRetour: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)) {
        if (onRetour != null) {
            CocoIconButton(icone = CocoIcons.Fermer, contentDescription = "Fermer sans enregistrer", onClick = onRetour)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = titre,
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            if (sousTitre != null) {
                Text(text = sousTitre, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** Titre de section, lu comme un titre par TalkBack. */
@Composable
internal fun TitreSection(texte: String, modifier: Modifier = Modifier) {
    Text(
        text = texte,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .padding(top = Dimens.Space3)
            .semantics { heading() },
    )
}

/** Texte d'aide ou d'erreur sous un champ. Une erreur est annoncée par TalkBack dès qu'elle paraît. */
@Composable
internal fun TexteSousChamp(texte: String, erreur: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = texte,
        style = MaterialTheme.typography.bodySmall,
        color = if (erreur) scheme.error else scheme.onSurfaceVariant,
        modifier = if (erreur) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
    )
}

/**
 * Champ de date : libellé visible, bouton qui affiche la date choisie et ouvre le calendrier.
 * Seuls les jours passés et aujourd'hui sont proposés.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChampDate(
    libelle: String,
    date: LocalDate?,
    aujourdhui: LocalDate,
    onChoisir: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    texteVide: String = "Choisir une date",
    aide: String? = null,
) {
    var ouvert by rememberSaveable { mutableStateOf(false) }
    val texte = date?.let(::dateComplete) ?: texteVide
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.Space1)) {
        Text(text = libelle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        CocoButton(
            texte = texte,
            onClick = { ouvert = true },
            style = CocoButtonStyle.Contour,
            icone = CocoIcons.Agenda,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$libelle : $texte. Changer la date" },
        )
        if (aide != null) TexteSousChamp(aide)
    }
    if (ouvert) {
        val limite = aujourdhui.versMillis()
        val etat = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: aujourdhui).versMillis(),
            yearRange = (aujourdhui.year - 3)..aujourdhui.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= limite
                override fun isSelectableYear(year: Int) = year <= aujourdhui.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { ouvert = false },
            confirmButton = {
                CocoButton(
                    texte = "Choisir ce jour",
                    style = CocoButtonStyle.Texte,
                    enabled = etat.selectedDateMillis != null,
                    onClick = {
                        etat.selectedDateMillis?.let { onChoisir(depuisMillis(it)) }
                        ouvert = false
                    },
                )
            },
            dismissButton = {
                CocoButton(texte = "Annuler", style = CocoButtonStyle.Texte, onClick = { ouvert = false })
            },
        ) {
            DatePicker(state = etat, title = { Text(libelle, Modifier.padding(start = Dimens.Space6, top = Dimens.Space4)) })
        }
    }
}

/** Compteur de jours avec deux boutons, borné à [plage]. */
@Composable
internal fun CompteurJours(
    libelle: String,
    valeur: Int,
    plage: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    aide: String? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.Space1)) {
        Text(text = libelle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Space3)) {
            CocoIconButton(
                icone = CocoIcons.Moins,
                contentDescription = "$libelle : un jour de moins",
                onClick = { onChange(valeur - 1) },
                enabled = valeur > plage.first,
                tonal = true,
            )
            Text(
                text = jours(valeur),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = Dimens.Space12 * 2)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            CocoIconButton(
                icone = CocoIcons.Plus,
                contentDescription = "$libelle : un jour de plus",
                onClick = { onChange(valeur + 1) },
                enabled = valeur < plage.last,
                tonal = true,
            )
        }
        if (aide != null) TexteSousChamp(aide)
    }
}

private fun LocalDate.versMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun depuisMillis(millis: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(millis, 86_400_000L))
