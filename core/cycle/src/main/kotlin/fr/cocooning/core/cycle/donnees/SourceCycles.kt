package fr.cocooning.core.cycle.donnees

import fr.cocooning.core.cycle.domaine.CycleSaisi
import fr.cocooning.core.cycle.domaine.ParametresCycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** Lecture et écriture brutes des cycles. Les règles métier sont dans [CycleRepositoryLocal]. */
interface SourceCycles {
    fun observerCycles(): Flow<List<CycleSaisi>>
    fun observerParametres(): Flow<ParametresCycle?>
    suspend fun cycles(): List<CycleSaisi>
    suspend fun parametres(): ParametresCycle?
    suspend fun ajouter(debut: LocalDate): Long
    suspend fun modifier(cycle: CycleSaisi)
    suspend fun supprimer(cycleId: Long)
    suspend fun enregistrerParametres(parametres: ParametresCycle)

    /** Exécute [bloc] d'un seul tenant : deux appuis rapprochés ne lisent pas le même état. */
    suspend fun <R> enTransaction(bloc: suspend () -> R): R
}

/** Source en mémoire, pour les tests et les aperçus. Rien n'est conservé. */
class SourceCyclesMemoire(
    initial: List<CycleSaisi> = emptyList(),
    parametres: ParametresCycle? = null,
) : SourceCycles {
    private val etat = MutableStateFlow(initial.sortedBy { it.debut })
    private val params = MutableStateFlow(parametres)
    private val verrou = Mutex()
    private var prochainId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    /** Si non nul, chaque écriture échoue avec cette exception (tests de la frontière d'erreur). */
    var panne: Exception? = null

    override fun observerCycles(): Flow<List<CycleSaisi>> = etat.asStateFlow()
    override fun observerParametres(): Flow<ParametresCycle?> = params.asStateFlow()
    override suspend fun cycles(): List<CycleSaisi> = etat.value
    override suspend fun parametres(): ParametresCycle? = params.value

    override suspend fun ajouter(debut: LocalDate): Long {
        panne?.let { throw it }
        val id = prochainId++
        etat.update { (it + CycleSaisi(id, debut)).sortedBy { c -> c.debut } }
        return id
    }

    override suspend fun modifier(cycle: CycleSaisi) {
        panne?.let { throw it }
        etat.update { liste -> liste.map { if (it.id == cycle.id) cycle else it }.sortedBy { it.debut } }
    }

    override suspend fun supprimer(cycleId: Long) {
        panne?.let { throw it }
        etat.update { liste -> liste.filterNot { it.id == cycleId } }
    }

    override suspend fun enregistrerParametres(parametres: ParametresCycle) {
        panne?.let { throw it }
        params.value = parametres
    }

    override suspend fun <R> enTransaction(bloc: suspend () -> R): R = verrou.withLock { bloc() }
}
