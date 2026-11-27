package fr.cocooning.core.cycle.donnees

import fr.cocooning.core.cycle.domaine.ConfigurationInitiale
import fr.cocooning.core.cycle.domaine.CycleSaisi
import fr.cocooning.core.cycle.domaine.ErreurSaisie
import fr.cocooning.core.cycle.domaine.OperationCycle
import fr.cocooning.core.cycle.domaine.ParametresCycle
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Résultat d'une saisie. Les exceptions du stockage ne sortent jamais du dépôt. */
sealed interface ResultatSaisie {
    /** @param operation ce que le bouton de l'accueil a fait, nul pour une correction. */
    data class Enregistree(val operation: OperationCycle?) : ResultatSaisie

    data class Refusee(val erreur: ErreurSaisie) : ResultatSaisie

    data class Echec(val cause: Throwable) : ResultatSaisie
}

/**
 * Historique du cycle de madame, stocké sur son téléphone. Les calculs (phase, jour J,
 * prédictions) se font à partir de ce flux avec
 * [fr.cocooning.core.cycle.domaine.CalculateurCycle].
 */
interface CycleRepository {
    /** Tous les cycles saisis, du plus ancien au plus récent, à chaque changement. */
    fun historique(): Flow<List<CycleSaisi>>

    /** Durées déclarées (EF-CYC-02), ou les valeurs par défaut si rien n'a été déclaré. */
    fun parametres(): Flow<ParametresCycle>

    /** Ce que madame a déclaré, nul tant qu'elle n'a pas fait la saisie du premier lancement. */
    fun parametresDeclares(): Flow<ParametresCycle?>

    /**
     * Saisie du premier lancement (EF-CYC-02) : enregistre les durées habituelles et la
     * contraception et, si elle est donnée, la date des dernières règles comme premier cycle.
     * Sert aussi à modifier ces réglages plus tard (sans date de règles).
     */
    suspend fun configurer(configuration: ConfigurationInitiale, aujourdhui: LocalDate): ResultatSaisie

    /** Action du bouton de l'accueil (EF-CYC-01) : début, fin, ou annulation d'une fin saisie aujourd'hui. */
    suspend fun basculerRegles(aujourdhui: LocalDate): ResultatSaisie

    /** Corrige le premier jour des règles d'un cycle. */
    suspend fun corrigerDebut(cycleId: Long, debut: LocalDate, aujourdhui: LocalDate): ResultatSaisie

    /** Saisit, corrige ou efface ([fin] nulle) le dernier jour des règles d'un cycle. */
    suspend fun corrigerFin(cycleId: Long, fin: LocalDate?, aujourdhui: LocalDate): ResultatSaisie

    /** Corrige d'un coup le début et la fin ([fin] nulle pour l'effacer) des règles d'un cycle. */
    suspend fun corriger(cycleId: Long, debut: LocalDate, fin: LocalDate?, aujourdhui: LocalDate): ResultatSaisie

    /** Ajoute des règles passées oubliées, avec leur fin si madame la connaît. */
    suspend fun ajouter(debut: LocalDate, fin: LocalDate?, aujourdhui: LocalDate): ResultatSaisie

    /** Supprime un cycle saisi par erreur. */
    suspend fun supprimer(cycleId: Long): ResultatSaisie

    suspend fun enregistrerParametres(parametres: ParametresCycle): ResultatSaisie
}
