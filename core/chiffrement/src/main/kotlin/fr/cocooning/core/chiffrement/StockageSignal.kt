package fr.cocooning.core.chiffrement

/**
 * Stockage brut de l'état Signal : des octets rangés par clé, sans aucune règle.
 *
 * Les règles (confiance dans les identités, détection d'une pré-clé réutilisée, convention des
 * appareils) sont dans [MagasinSignal], écrit une seule fois et testé sur la JVM. Deux
 * implémentations : [StockageSignalMemoire] pour les tests, et la base Room chiffrée
 * `cocooning_signal.db` ([fr.cocooning.core.chiffrement.room.StockageSignalRoom]).
 *
 * Les appels sont bloquants : on n'appelle jamais ce stockage depuis le fil principal.
 */
interface StockageSignal {
    class IdentiteLocale(val paire: ByteArray, val idEnregistrement: Int)

    fun identiteLocale(): IdentiteLocale?
    fun enregistrerIdentiteLocale(identite: IdentiteLocale)

    fun identiteDistante(nom: String, appareil: Int): ByteArray?
    fun enregistrerIdentiteDistante(nom: String, appareil: Int, cle: ByteArray)
    fun supprimerIdentiteDistante(nom: String, appareil: Int)

    fun preCle(id: Int): ByteArray?
    fun enregistrerPreCle(id: Int, enregistrement: ByteArray)
    fun supprimerPreCle(id: Int)
    fun nombrePreCles(): Int

    fun preCleSignee(id: Int): ByteArray?
    fun preClesSignees(): List<ByteArray>
    fun enregistrerPreCleSignee(id: Int, enregistrement: ByteArray)
    fun supprimerPreCleSignee(id: Int)

    fun preCleKyber(id: Int): ByteArray?
    fun preClesKyber(): List<ByteArray>
    fun enregistrerPreCleKyber(id: Int, enregistrement: ByteArray)
    fun supprimerPreCleKyber(id: Int)

    /** Vrai si ce trio a déjà servi ; sinon le note et renvoie faux, d'un seul tenant. */
    fun noterUsageKyber(idKyber: Int, idSignee: Int, cleDeBase: ByteArray): Boolean

    fun session(nom: String, appareil: Int): ByteArray?
    fun enregistrerSession(nom: String, appareil: Int, enregistrement: ByteArray)
    fun supprimerSession(nom: String, appareil: Int)
    fun supprimerSessions(nom: String)
    fun appareilsAvecSession(nom: String): List<Int>

    fun cleExpediteur(nom: String, appareil: Int, distribution: String): ByteArray?
    fun enregistrerCleExpediteur(nom: String, appareil: Int, distribution: String, enregistrement: ByteArray)

    fun compteur(nom: String): Int?
    fun enregistrerCompteur(nom: String, valeur: Int)

    /** Exécute [bloc] d'un seul tenant : une session à moitié écrite casserait la conversation. */
    fun <R> enTransaction(bloc: () -> R): R
}

/** Stockage en mémoire, pour les tests. Rien n'est conservé. */
class StockageSignalMemoire : StockageSignal {
    private data class Cle(val nom: String, val appareil: Int)
    private data class CleExpediteur(val nom: String, val appareil: Int, val distribution: String)

    private var identite: StockageSignal.IdentiteLocale? = null
    private val identites = mutableMapOf<Cle, ByteArray>()
    private val preCles = mutableMapOf<Int, ByteArray>()
    private val signees = mutableMapOf<Int, ByteArray>()
    private val kyber = mutableMapOf<Int, ByteArray>()
    private val usagesKyber = mutableSetOf<String>()
    private val sessions = mutableMapOf<Cle, ByteArray>()
    private val expediteurs = mutableMapOf<CleExpediteur, ByteArray>()
    private val compteurs = mutableMapOf<String, Int>()

    override fun identiteLocale() = identite
    override fun enregistrerIdentiteLocale(identite: StockageSignal.IdentiteLocale) {
        this.identite = identite
    }

    override fun identiteDistante(nom: String, appareil: Int) = identites[Cle(nom, appareil)]?.copyOf()
    override fun enregistrerIdentiteDistante(nom: String, appareil: Int, cle: ByteArray) {
        identites[Cle(nom, appareil)] = cle.copyOf()
    }
    override fun supprimerIdentiteDistante(nom: String, appareil: Int) {
        identites.remove(Cle(nom, appareil))
    }

    override fun preCle(id: Int) = preCles[id]?.copyOf()
    override fun enregistrerPreCle(id: Int, enregistrement: ByteArray) {
        preCles[id] = enregistrement.copyOf()
    }
    override fun supprimerPreCle(id: Int) {
        preCles.remove(id)
    }
    override fun nombrePreCles() = preCles.size

    override fun preCleSignee(id: Int) = signees[id]?.copyOf()
    override fun preClesSignees() = signees.entries.sortedBy { it.key }.map { it.value.copyOf() }
    override fun enregistrerPreCleSignee(id: Int, enregistrement: ByteArray) {
        signees[id] = enregistrement.copyOf()
    }
    override fun supprimerPreCleSignee(id: Int) {
        signees.remove(id)
    }

    override fun preCleKyber(id: Int) = kyber[id]?.copyOf()
    override fun preClesKyber() = kyber.entries.sortedBy { it.key }.map { it.value.copyOf() }
    override fun enregistrerPreCleKyber(id: Int, enregistrement: ByteArray) {
        kyber[id] = enregistrement.copyOf()
    }
    override fun supprimerPreCleKyber(id: Int) {
        kyber.remove(id)
    }

    override fun noterUsageKyber(idKyber: Int, idSignee: Int, cleDeBase: ByteArray): Boolean =
        !usagesKyber.add("$idKyber/$idSignee/${cleDeBase.joinToString("") { "%02x".format(it) }}")

    override fun session(nom: String, appareil: Int) = sessions[Cle(nom, appareil)]?.copyOf()
    override fun enregistrerSession(nom: String, appareil: Int, enregistrement: ByteArray) {
        sessions[Cle(nom, appareil)] = enregistrement.copyOf()
    }
    override fun supprimerSession(nom: String, appareil: Int) {
        sessions.remove(Cle(nom, appareil))
    }
    override fun supprimerSessions(nom: String) {
        sessions.keys.removeAll { it.nom == nom }
    }
    override fun appareilsAvecSession(nom: String) = sessions.keys.filter { it.nom == nom }.map { it.appareil }.sorted()

    override fun cleExpediteur(nom: String, appareil: Int, distribution: String) =
        expediteurs[CleExpediteur(nom, appareil, distribution)]?.copyOf()
    override fun enregistrerCleExpediteur(nom: String, appareil: Int, distribution: String, enregistrement: ByteArray) {
        expediteurs[CleExpediteur(nom, appareil, distribution)] = enregistrement.copyOf()
    }

    override fun compteur(nom: String) = compteurs[nom]
    override fun enregistrerCompteur(nom: String, valeur: Int) {
        compteurs[nom] = valeur
    }

    @Synchronized
    override fun <R> enTransaction(bloc: () -> R): R = bloc()
}
