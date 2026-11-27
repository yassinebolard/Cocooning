package fr.cocooning.core.cycle.domaine

import java.time.LocalDate

/** Modification de l'historique décidée à partir d'une action de madame. */
sealed interface OperationCycle {
    /** Nouveau cycle qui commence le [debut]. */
    data class Commencer(val debut: LocalDate) : OperationCycle

    /** Fin des règles du cycle [cycleId] le [fin]. */
    data class Terminer(val cycleId: Long, val fin: LocalDate) : OperationCycle

    /** Annule la fin saisie du cycle [cycleId] : les règles sont de nouveau en cours. */
    data class Rouvrir(val cycleId: Long) : OperationCycle
}

/** Raison pour laquelle une saisie ou une correction est refusée. */
enum class ErreurSaisie {
    /** Une date dans le futur. */
    DateFuture,

    /** La fin des règles précède leur début. */
    FinAvantDebut,

    /** Un autre cycle commence déjà ce jour-là. */
    DebutDejaPris,

    /** La date tombe pendant les règles du cycle précédent. */
    ChevaucheCyclePrecedent,

    /** La fin des règles atteint ou dépasse le début du cycle suivant. */
    ChevaucheCycleSuivant,

    /** Le cycle n'existe pas (supprimé entre-temps). */
    CycleInconnu,

    /** Durée habituelle du cycle hors de [CalculateurCycle.CYCLE_PLAUSIBLE]. */
    DureeCycleHorsLimites,

    /** Durée habituelle des règles hors de [CalculateurCycle.REGLES_PLAUSIBLES]. */
    DureeReglesHorsLimites,

    /** Une fin des règles sans leur début. */
    FinSansDebut,
}

/** Règles de saisie et de correction, sans stockage. */
object SaisiesCycle {

    /**
     * Ce que fait le bouton de l'accueil (EF-CYC-01) :
     * - règles en cours : elles se terminent aujourd'hui ;
     * - la fin a été saisie aujourd'hui (appui en trop) : elle est annulée ;
     * - sinon, un nouveau cycle commence aujourd'hui, y compris quand la fin du cycle précédent
     *   a été oubliée (elle reste alors estimée).
     */
    fun basculer(historique: List<CycleSaisi>, parametres: ParametresCycle, aujourdhui: LocalDate): OperationCycle {
        val dernier = historique.filter { !it.debut.isAfter(aujourdhui) }.maxByOrNull { it.debut }
            ?: return OperationCycle.Commencer(aujourdhui)
        val etat = CalculateurCycle.etat(historique, parametres, aujourdhui) as EtatCycle.Suivi
        return when {
            etat.reglesEnCours -> OperationCycle.Terminer(dernier.id, aujourdhui)
            dernier.finRegles != null && !dernier.finRegles.isBefore(aujourdhui) -> OperationCycle.Rouvrir(dernier.id)
            else -> OperationCycle.Commencer(aujourdhui)
        }
    }

    /** Vérifie qu'un nouveau cycle peut commencer le [debut], avec sa [fin] si elle est connue. */
    fun validerNouveauDebut(
        historique: List<CycleSaisi>,
        debut: LocalDate,
        aujourdhui: LocalDate,
        fin: LocalDate? = null,
    ): ErreurSaisie? = validerCycle(historique, CycleSaisi(id = SANS_ID, debut = debut, finRegles = fin), aujourdhui)

    /**
     * Vérifie la correction des deux dates d'un cycle à la fois. Déplacer des règles de plusieurs
     * jours passerait sinon par un état intermédiaire refusé (la fin avant le nouveau début).
     */
    fun validerCorrection(
        historique: List<CycleSaisi>,
        cycleId: Long,
        debut: LocalDate,
        fin: LocalDate?,
        aujourdhui: LocalDate,
    ): ErreurSaisie? {
        val cycle = historique.firstOrNull { it.id == cycleId } ?: return ErreurSaisie.CycleInconnu
        return validerCycle(historique, cycle.copy(debut = debut, finRegles = fin), aujourdhui)
    }

    /** Vérifie ce que madame renseigne au premier lancement (EF-CYC-02). */
    fun validerConfiguration(
        historique: List<CycleSaisi>,
        configuration: ConfigurationInitiale,
        aujourdhui: LocalDate,
    ): ErreurSaisie? {
        val parametres = configuration.parametres
        if (parametres.dureeCycle !in CalculateurCycle.CYCLE_PLAUSIBLE) return ErreurSaisie.DureeCycleHorsLimites
        if (parametres.dureeRegles !in CalculateurCycle.REGLES_PLAUSIBLES) return ErreurSaisie.DureeReglesHorsLimites
        val debut = configuration.dernieresRegles
            ?: return if (configuration.finDernieresRegles != null) ErreurSaisie.FinSansDebut else null
        return validerNouveauDebut(historique, debut, aujourdhui, configuration.finDernieresRegles)
    }

    /** Vérifie la correction du début du cycle [cycleId]. */
    fun validerDebut(historique: List<CycleSaisi>, cycleId: Long, debut: LocalDate, aujourdhui: LocalDate): ErreurSaisie? {
        val cycle = historique.firstOrNull { it.id == cycleId } ?: return ErreurSaisie.CycleInconnu
        return validerCycle(historique, cycle.copy(debut = debut), aujourdhui)
    }

    /** Vérifie la saisie ou la correction de la fin des règles du cycle [cycleId] (nulle pour l'effacer). */
    fun validerFin(historique: List<CycleSaisi>, cycleId: Long, fin: LocalDate?, aujourdhui: LocalDate): ErreurSaisie? {
        val cycle = historique.firstOrNull { it.id == cycleId } ?: return ErreurSaisie.CycleInconnu
        return validerCycle(historique, cycle.copy(finRegles = fin), aujourdhui)
    }

    /** Vérifie [candidat] par rapport aux autres cycles de l'historique. */
    private fun validerCycle(historique: List<CycleSaisi>, candidat: CycleSaisi, aujourdhui: LocalDate): ErreurSaisie? {
        val fin = candidat.finRegles
        if (candidat.debut.isAfter(aujourdhui) || fin?.isAfter(aujourdhui) == true) return ErreurSaisie.DateFuture
        if (fin != null && fin.isBefore(candidat.debut)) return ErreurSaisie.FinAvantDebut

        val autres = historique.filter { it.id != candidat.id }
        if (autres.any { it.debut == candidat.debut }) return ErreurSaisie.DebutDejaPris

        val precedent = autres.filter { it.debut.isBefore(candidat.debut) }.maxByOrNull { it.debut }
        val suivant = autres.filter { it.debut.isAfter(candidat.debut) }.minByOrNull { it.debut }
        if (precedent?.finRegles != null && !precedent.finRegles.isBefore(candidat.debut)) {
            return ErreurSaisie.ChevaucheCyclePrecedent
        }
        if (suivant != null && fin != null && !fin.isBefore(suivant.debut)) return ErreurSaisie.ChevaucheCycleSuivant
        return null
    }

    private const val SANS_ID = Long.MIN_VALUE
}
