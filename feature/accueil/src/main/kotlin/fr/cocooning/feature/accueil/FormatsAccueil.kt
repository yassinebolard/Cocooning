package fr.cocooning.feature.accueil

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/* Mise en forme des dates et heures de l'accueil, en français. Espaces insécables autour de « h ». */

private val Francais: Locale = Locale.FRENCH
private val JourComplet = DateTimeFormatter.ofPattern("EEEE d MMMM", Francais)
private val JourMois = DateTimeFormatter.ofPattern("d MMMM", Francais)

/** « Vendredi 27 novembre ». */
internal fun jourComplet(date: LocalDate): String =
    JourComplet.format(date).replaceFirstChar { it.titlecase(Francais) }

/** « 8 h 12 », « 19 h 30 », « 9 h ». */
internal fun heure(h: LocalTime): String =
    if (h.minute == 0) "${h.hour} h" else "${h.hour} h %02d".format(h.minute)

/** « entre le 3 et le 6 décembre », ou « entre le 30 novembre et le 2 décembre ». */
internal fun fourchette(plage: ClosedRange<LocalDate>): String {
    val debut = plage.start
    val fin = plage.endInclusive
    val texteDebut = if (debut.month == fin.month) debut.dayOfMonth.toString() else JourMois.format(debut)
    return "entre le $texteDebut et le ${JourMois.format(fin)}"
}

/** « Aujourd'hui », « Demain » ou « Samedi 28 novembre ». */
internal fun jourRelatif(jour: LocalDate, aujourdhui: LocalDate): String = when (jour) {
    aujourdhui -> "Aujourd'hui"
    aujourdhui.plusDays(1) -> "Demain"
    else -> jourComplet(jour)
}
