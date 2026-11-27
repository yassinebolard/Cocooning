package fr.cocooning.feature.cycle

import fr.cocooning.core.cycle.domaine.CalculateurCycle
import fr.cocooning.core.cycle.domaine.Contraception
import fr.cocooning.core.cycle.domaine.CyclePasse
import fr.cocooning.core.cycle.domaine.ErreurSaisie
import fr.cocooning.core.cycle.domaine.Estimation
import fr.cocooning.core.cycle.donnees.ResultatSaisie
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/* Textes de l'onglet Cycle, en français, tutoiement. Aucune date n'est dite certaine. */

private val Francais: Locale = Locale.FRENCH
private val JourMois = DateTimeFormatter.ofPattern("d MMMM", Francais)
private val JourMoisAnnee = DateTimeFormatter.ofPattern("d MMMM yyyy", Francais)

/** « 3 novembre », avec l'année quand elle diffère de celle de [aujourdhui]. */
internal fun date(jour: LocalDate, aujourdhui: LocalDate): String =
    if (jour.year == aujourdhui.year) JourMois.format(jour) else JourMoisAnnee.format(jour)

/** « 3 novembre 2026 », pour les champs de date. */
internal fun dateComplete(jour: LocalDate): String = JourMoisAnnee.format(jour)

/** « entre le 3 et le 6 décembre », ou « entre le 30 novembre et le 2 décembre ». */
internal fun fourchette(plage: ClosedRange<LocalDate>, aujourdhui: LocalDate): String {
    val debut = plage.start
    val fin = plage.endInclusive
    val texteDebut = if (debut.month == fin.month && debut.year == fin.year) debut.dayOfMonth.toString() else date(debut, aujourdhui)
    return "entre le $texteDebut et le ${date(fin, aujourdhui)}"
}

internal fun jours(n: Int) = if (n == 1) "1 jour" else "$n jours"

/** Titre d'une ligne de l'historique : « Du 3 au 7 novembre », « Depuis le 3 novembre ». */
internal fun titreCycle(passe: CyclePasse, aujourdhui: LocalDate): String {
    val debut = passe.cycle.debut
    val fin = passe.cycle.finRegles
    return when {
        fin == null && passe.dureeCycle == null -> "Depuis le ${date(debut, aujourdhui)}"
        fin == null -> "Le ${date(debut, aujourdhui)}"
        fin == debut -> "Le ${date(debut, aujourdhui)}"
        debut.month == fin.month && debut.year == fin.year -> "Du ${debut.dayOfMonth} au ${date(fin, aujourdhui)}"
        else -> "Du ${date(debut, aujourdhui)} au ${date(fin, aujourdhui)}"
    }
}

/** Lignes sous le titre d'une ligne de l'historique. */
internal fun detailsCycle(passe: CyclePasse): List<String> = buildList {
    add(
        when (val regles = passe.dureeRegles) {
            null -> if (passe.dureeCycle == null) "Fin des règles pas encore indiquée" else "Fin des règles non indiquée"
            else -> "Règles : ${jours(regles)}"
        },
    )
    passe.dureeCycle?.let { duree ->
        add("Cycle : ${jours(duree)}")
        if (duree !in CalculateurCycle.CYCLE_PLAUSIBLE) {
            add("Non compté dans la moyenne : durée inhabituelle. Des règles ont peut-être été oubliées entre les deux.")
        }
    } ?: add("Cycle en cours")
}

/** « 29 jours en moyenne, d'après tes 3 derniers cycles », ou la valeur déclarée tant qu'il n'y a rien. */
internal fun texteMoyenneCycle(estimation: Estimation): String = when (estimation.cyclesObserves) {
    0 -> "${jours(estimation.jours)}, la durée habituelle que tu as indiquée"
    1 -> "${jours(estimation.jours)}, d'après ton dernier cycle et la durée habituelle indiquée"
    else -> "${jours(estimation.jours)} en moyenne, d'après tes ${estimation.cyclesObserves} derniers cycles"
}

/** Même chose pour la durée des règles. */
internal fun texteMoyenneRegles(estimation: Estimation): String = when (estimation.cyclesObserves) {
    0 -> "${jours(estimation.jours)}, la durée habituelle que tu as indiquée"
    1 -> "${jours(estimation.jours)}, d'après tes dernières règles et la durée habituelle indiquée"
    else -> "${jours(estimation.jours)} en moyenne, d'après tes ${estimation.cyclesObserves} dernières règles"
}

internal fun libelle(contraception: Contraception): String = when (contraception) {
    Contraception.Aucune -> "Aucune"
    Contraception.Preservatif -> "Préservatif"
    Contraception.SteriletCuivre -> "Stérilet au cuivre"
    Contraception.Pilule -> "Pilule"
    Contraception.SteriletHormonal -> "Stérilet hormonal"
    Contraception.Implant -> "Implant"
    Contraception.AnneauOuPatch -> "Anneau ou patch"
    Contraception.Autre -> "Autre"
}

/** Ce qu'il faut corriger, dit simplement. */
internal fun message(erreur: ErreurSaisie): String = when (erreur) {
    ErreurSaisie.DateFuture -> "Cette date est dans le futur. Choisis aujourd'hui ou un jour passé."
    ErreurSaisie.FinAvantDebut -> "Le dernier jour des règles doit venir après le premier."
    ErreurSaisie.DebutDejaPris -> "Des règles commencent déjà ce jour-là."
    ErreurSaisie.ChevaucheCyclePrecedent ->
        "Ce jour tombe pendant les règles précédentes. Corrige d'abord leur dernier jour."
    ErreurSaisie.ChevaucheCycleSuivant -> "Le dernier jour doit venir avant le début des règles suivantes."
    ErreurSaisie.CycleInconnu -> "Ces règles ont été supprimées entre-temps."
    ErreurSaisie.DureeCycleHorsLimites ->
        "Indique une durée de cycle entre ${CalculateurCycle.CYCLE_PLAUSIBLE.first} et ${CalculateurCycle.CYCLE_PLAUSIBLE.last} jours."
    ErreurSaisie.DureeReglesHorsLimites ->
        "Indique une durée de règles entre ${CalculateurCycle.REGLES_PLAUSIBLES.first} et ${CalculateurCycle.REGLES_PLAUSIBLES.last} jours."
    ErreurSaisie.FinSansDebut -> "Indique d'abord le premier jour des règles."
}

internal const val MESSAGE_ECHEC = "Ça n'a pas pu être enregistré. Réessaie dans un instant."

/** Message d'erreur d'une saisie refusée ou échouée, nul si elle est enregistrée. */
internal fun erreurDe(resultat: ResultatSaisie): String? = when (resultat) {
    is ResultatSaisie.Enregistree -> null
    is ResultatSaisie.Refusee -> message(resultat.erreur)
    is ResultatSaisie.Echec -> MESSAGE_ECHEC
}

internal const val AVERTISSEMENT_MEDICAL =
    "Cocooning n'est ni un moyen de contraception ni un dispositif médical : ses prévisions sont des estimations."
