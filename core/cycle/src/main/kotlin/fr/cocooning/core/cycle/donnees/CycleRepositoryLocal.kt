package fr.cocooning.core.cycle.donnees

import fr.cocooning.core.cycle.domaine.CycleSaisi
import fr.cocooning.core.cycle.domaine.ErreurSaisie
import fr.cocooning.core.cycle.domaine.OperationCycle
import fr.cocooning.core.cycle.domaine.ParametresCycle
import fr.cocooning.core.cycle.domaine.SaisiesCycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Dépôt du cycle sur le téléphone : valide chaque saisie puis l'écrit dans [source]. */
class CycleRepositoryLocal(private val source: SourceCycles) : CycleRepository {

    override fun historique(): Flow<List<CycleSaisi>> = source.observerCycles()

    override fun parametres(): Flow<ParametresCycle> = source.observerParametres().map { it ?: ParametresCycle() }

    override suspend fun basculerRegles(aujourdhui: LocalDate): ResultatSaisie = protege {
        val cycles = source.cycles()
        val parametres = source.parametres() ?: ParametresCycle()
        when (val operation = SaisiesCycle.basculer(cycles, parametres, aujourdhui)) {
            is OperationCycle.Commencer -> {
                val erreur = SaisiesCycle.validerNouveauDebut(cycles, operation.debut, aujourdhui)
                if (erreur != null) {
                    ResultatSaisie.Refusee(erreur)
                } else {
                    source.ajouter(operation.debut)
                    ResultatSaisie.Enregistree(operation)
                }
            }
            is OperationCycle.Terminer -> {
                source.modifier(cycles.first { it.id == operation.cycleId }.copy(finRegles = operation.fin))
                ResultatSaisie.Enregistree(operation)
            }
            is OperationCycle.Rouvrir -> {
                source.modifier(cycles.first { it.id == operation.cycleId }.copy(finRegles = null))
                ResultatSaisie.Enregistree(operation)
            }
        }
    }

    override suspend fun corrigerDebut(cycleId: Long, debut: LocalDate, aujourdhui: LocalDate): ResultatSaisie =
        protege {
            val cycles = source.cycles()
            val erreur = SaisiesCycle.validerDebut(cycles, cycleId, debut, aujourdhui)
            if (erreur != null) {
                ResultatSaisie.Refusee(erreur)
            } else {
                source.modifier(cycles.first { it.id == cycleId }.copy(debut = debut))
                ResultatSaisie.Enregistree(null)
            }
        }

    override suspend fun corrigerFin(cycleId: Long, fin: LocalDate?, aujourdhui: LocalDate): ResultatSaisie =
        protege {
            val cycles = source.cycles()
            val erreur = SaisiesCycle.validerFin(cycles, cycleId, fin, aujourdhui)
            if (erreur != null) {
                ResultatSaisie.Refusee(erreur)
            } else {
                source.modifier(cycles.first { it.id == cycleId }.copy(finRegles = fin))
                ResultatSaisie.Enregistree(null)
            }
        }

    override suspend fun supprimer(cycleId: Long): ResultatSaisie = protege {
        if (source.cycles().none { it.id == cycleId }) {
            ResultatSaisie.Refusee(ErreurSaisie.CycleInconnu)
        } else {
            source.supprimer(cycleId)
            ResultatSaisie.Enregistree(null)
        }
    }

    override suspend fun enregistrerParametres(parametres: ParametresCycle): ResultatSaisie = protege {
        source.enregistrerParametres(parametres)
        ResultatSaisie.Enregistree(null)
    }

    /** Frontière d'erreur : une exception du stockage devient un [ResultatSaisie.Echec]. */
    private suspend fun protege(bloc: suspend () -> ResultatSaisie): ResultatSaisie = try {
        source.enTransaction { bloc() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ResultatSaisie.Echec(e)
    }
}
