package fr.cocooning.core.reseau

import fr.cocooning.core.chiffrement.AdresseAppareil
import fr.cocooning.core.chiffrement.ChangementIdentite
import fr.cocooning.core.chiffrement.EnveloppeChiffree
import fr.cocooning.core.chiffrement.ExceptionChiffrement
import fr.cocooning.core.chiffrement.MoteurSignal
import fr.cocooning.core.chiffrement.PaquetDestinataire
import fr.cocooning.core.chiffrement.PreClePublique
import fr.cocooning.core.chiffrement.TypeEnveloppe
import fr.cocooning.core.messagerie.domaine.ContenuMessage
import fr.cocooning.core.messagerie.domaine.IdConversation
import fr.cocooning.core.messagerie.domaine.IdMembre
import fr.cocooning.core.messagerie.domaine.IdMessage
import fr.cocooning.core.messagerie.domaine.Message
import fr.cocooning.core.messagerie.domaine.TypeAccuse
import fr.cocooning.core.messagerie.donnees.EvenementRecu
import fr.cocooning.core.messagerie.donnees.FormatMessages
import fr.cocooning.core.messagerie.donnees.MessagerieRepository
import fr.cocooning.core.messagerie.donnees.ResultatMessagerie
import fr.cocooning.core.messagerie.donnees.appliquer
import fr.cocooning.protocole.api.ContenuSigne
import fr.cocooning.protocole.api.CreationEspace
import fr.cocooning.protocole.api.DemandeJeton
import fr.cocooning.protocole.api.EnvoiAppareil
import fr.cocooning.protocole.api.EnvoiMessage
import fr.cocooning.protocole.api.EspaceApi
import fr.cocooning.protocole.api.Inscription
import fr.cocooning.protocole.api.Invitation
import fr.cocooning.protocole.api.PaquetApi
import fr.cocooning.protocole.api.PreCleApi
import fr.cocooning.protocole.api.PublicationClesApi
import fr.cocooning.protocole.api.RejoindreEspace
import fr.cocooning.protocole.api.RemplacerAppareil
import fr.cocooning.protocole.api.TypeEnveloppeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.UUID
import kotlin.coroutines.CoroutineContext

/** Bilan d'une synchronisation, pour l'écran et le journal. */
data class RapportSynchro(
    val envoyes: Int = 0,
    val enEchec: Int = 0,
    val recus: Int = 0,
    /** Membres dont le téléphone a changé : afficher « le code de sécurité a changé ». */
    val identitesChangees: Set<IdMembre> = emptySet(),
)

/**
 * Le transport de la messagerie (ADR 0003, section 7 ; ADR 0004) : relie le dépôt local, le
 * chiffrement Signal et le serveur. Il ne garde aucun état propre : la file d'attente est dans
 * le dépôt (messages « en attente »), les clés dans le moteur, l'inscription dans [compte].
 *
 * Toutes les opérations réseau sont sérialisées ; à appeler hors du fil principal
 * ([contexte] par défaut : Dispatchers.IO, à cause des appels bloquants du moteur Signal).
 */
class Synchroniseur(
    private val api: ApiCocooning,
    private val cle: CleAppareil,
    private val compte: MagasinCompte,
    private val moteur: MoteurSignal,
    /** Le dépôt dépend du membre, connu seulement après l'inscription : il est fourni à la demande. */
    private val fournirDepot: () -> MessagerieRepository,
    private val horloge: Clock = Clock.systemUTC(),
    private val contexte: CoroutineContext = Dispatchers.IO,
) {
    private val depot: MessagerieRepository get() = fournirDepot()
    private val verrou = Mutex()
    private var jeton: Pair<String, Long>? = null

    /** L'unique conversation d'un espace en V1 porte l'identifiant de l'espace. */
    suspend fun conversation(): IdConversation? = compte.lire()?.let { IdConversation(it.espace) }

    suspend fun moi(): IdMembre? = compte.lire()?.let { IdMembre(it.membre) }

    // Inscription

    /** Premier membre : crée l'espace et renvoie le code à donner à l'autre (EF-CPT-02). */
    suspend fun creerEspace(): Invitation = operation {
        val secret = nouveauSecret()
        val inscription = api.creerEspace(CreationEspace(cle.creer(), sha256(secret)))
        terminerInscription(inscription, secret)
        checkNotNull(inscription.invitation) { "Le serveur n'a pas donné de code d'invitation" }
    }

    /** Second membre : rejoint l'espace avec le code (EF-CPT-03). */
    suspend fun rejoindre(code: String) = operation {
        val secret = nouveauSecret()
        terminerInscription(api.rejoindre(RejoindreEspace(code.trim(), cle.creer(), sha256(secret))), secret)
    }

    /**
     * Après une restauration (EF-CPT-09) : le compte vient de la sauvegarde, mais la clé du
     * Keystore et l'identité Signal sont neuves. Le secret de récupération fait reprendre la place
     * de l'ancien téléphone, puis un nouveau secret est tiré.
     */
    suspend fun reprendreApresRestauration() = operation {
        val ancien = checkNotNull(compte.lire()) { "Aucun compte à reprendre" }
        cle.supprimer()
        val secret = nouveauSecret()
        val inscription = api.remplacer(
            RemplacerAppareil(ancien.membre, ancien.secretRecuperation, cle.creer(), sha256(secret)),
        )
        terminerInscription(inscription, secret)
    }

    /** Vrai si ce téléphone a un compte mais plus sa clé d'appareil (sauvegarde restaurée). */
    suspend fun doitReprendre(): Boolean = compte.lire() != null && !cle.existe()

    private suspend fun terminerInscription(inscription: Inscription, secret: ByteArray) {
        compte.ecrire(Compte(inscription.espace, inscription.membre, inscription.appareil, inscription.numero, secret))
        jeton = null
        withContext(contexte) { moteur.initialiser() }
        publier()
    }

    /** Code d'invitation de plus (V2 : inviter un autre membre). */
    suspend fun inviter(): Invitation = operation { authentifie { api.inviter(it) } }

    suspend fun espace(): EspaceApi = operation { authentifie { api.espace(it) } }

    // Clés

    /** Publie les clés Signal si le moteur en a de nouvelles (première fois, renouvellement, recharge). */
    suspend fun publierCles() = operation { publier() }

    private suspend fun publier() {
        val publication = withContext(contexte) { moteur.clesAPublier() } ?: return
        authentifie {
            api.publierCles(
                it,
                PublicationClesApi(
                    idEnregistrement = publication.idEnregistrement,
                    identite = publication.identite,
                    preCleSignee = publication.preCleSignee.versApi(),
                    preCleKyber = publication.preCleKyber.versApi(),
                    preClesUniques = publication.preClesUniques.map { cle -> cle.versApi() },
                ),
            )
        }
    }

    // Synchronisation

    /** Envoie la file d'attente, relève la boîte, publie les clés si besoin. */
    suspend fun synchroniser(): RapportSynchro = operation {
        val envoi = envoyerEnAttente()
        val releve = relever()
        publier()
        RapportSynchro(envoi.envoyes, envoi.enEchec, releve.recus, envoi.identitesChangees + releve.identitesChangees)
    }

    /**
     * Envoie chaque message en attente à tous les appareils de ses destinataires (EF-MSG-10).
     * Une coupure réseau arrête la file sans rien perdre : les messages restent en attente.
     */
    private suspend fun envoyerEnAttente(): RapportSynchro {
        val enAttente = depot.aEnvoyer()
        if (enAttente.isEmpty()) return RapportSynchro()
        var espace = authentifie { api.espace(it) }
        var envoyes = 0
        var enEchec = 0
        val changees = mutableSetOf<IdMembre>()
        for (message in enAttente) {
            val destinataires = message.accuses.map { it.destinataire }
            val octets = FormatMessages.texte(message)
            val resultat = try {
                deposer(message.id, destinataires, octets, espace, changees)
            } catch (e: ErreurReseau.Http) {
                when {
                    // Serveur en difficulté : le message reste en attente, on arrête la file.
                    e.statut >= 500 -> throw e
                    e.erreur?.code == "appareils_incoherents" -> {
                        // Un téléphone a été remplacé : on relit l'espace et on réessaie une fois.
                        espace = authentifie { api.espace(it) }
                        try {
                            deposer(message.id, destinataires, octets, espace, changees)
                        } catch (e: ErreurReseau.Http) {
                            if (e.statut >= 500) throw e
                            false
                        }
                    }
                    else -> false
                }
            } catch (e: ExceptionChiffrement) {
                false
            }
            if (resultat) {
                depot.marquerEnvoye(message.id)
                envoyes++
            } else {
                depot.marquerEchec(message.id)
                enEchec++
            }
        }
        return RapportSynchro(envoyes = envoyes, enEchec = enEchec, identitesChangees = changees)
    }

    /** Chiffre [octets] pour chaque appareil des [destinataires] et dépose le tout d'un coup. */
    private suspend fun deposer(
        id: IdMessage,
        destinataires: List<IdMembre>,
        octets: ByteArray,
        espace: EspaceApi,
        changees: MutableSet<IdMembre>,
    ): Boolean {
        val envois = mutableListOf<EnvoiAppareil>()
        for (membre in destinataires) {
            val appareils = espace.membres.firstOrNull { it.id == membre.valeur }?.appareils.orEmpty()
            noterAppareils(membre, appareils.map { it.id }, remplacer = true, changees)
            val sansSession = appareils.filterNot { withContext(contexte) { moteur.aUneSession(adresse(it.id, it.numero)) } }
            if (sansSession.isNotEmpty()) {
                val paquets = authentifie { api.paquets(it, membre.valeur) }.paquets
                for (paquet in paquets.filter { p -> sansSession.any { it.id == p.appareil } }) {
                    val changement = withContext(contexte) {
                        moteur.ouvrirSession(adresse(paquet.appareil, paquet.numero), paquet.versChiffrement())
                    }
                    if (changement == ChangementIdentite.Changee) changees += membre
                }
            }
            for (appareil in appareils) {
                val enveloppe = withContext(contexte) { moteur.chiffrer(adresse(appareil.id, appareil.numero), octets) }
                envois += EnvoiAppareil(appareil.id, enveloppe.type.versApi(), enveloppe.octets)
            }
        }
        if (envois.isEmpty()) return false
        authentifie { api.envoyer(it, EnvoiMessage(id.valeur, envois)) }
        return true
    }

    private class Releve(val recus: Int, val identitesChangees: Set<IdMembre>)

    /**
     * Relève la boîte, déchiffre, range chaque événement dans le dépôt, puis acquitte. Un message
     * illisible (falsifié, ou chiffré pour un ancien téléphone) est acquitté et ignoré ; un
     * message en double aussi. Les nouveaux textes reçoivent un accusé « reçu » (EF-MSG-07).
     */
    private suspend fun relever(): Releve {
        val moi = IdMembre(checkNotNull(compte.lire()).membre)
        var apres = 0L
        var recus = 0
        val changees = mutableSetOf<IdMembre>()
        val aAccuser = mutableMapOf<Pair<IdConversation, IdMembre>, MutableList<IdMessage>>()
        do {
            val releve = authentifie { api.relever(it, apres, LOT_RELEVE) }
            val traites = mutableListOf<Long>()
            for (recu in releve.messages) {
                val auteur = IdMembre(recu.expediteurMembre)
                val dechiffre = try {
                    withContext(contexte) {
                        moteur.dechiffrer(
                            adresse(recu.expediteurAppareil, recu.expediteurNumero),
                            EnveloppeChiffree(recu.type.versChiffrement(), recu.contenu),
                        )
                    }
                } catch (e: ExceptionChiffrement) {
                    null
                }
                if (dechiffre != null) {
                    if (dechiffre.identite == ChangementIdentite.Changee) changees += auteur
                    noterAppareils(auteur, listOf(recu.expediteurAppareil), remplacer = false, changees)
                    val evenement = FormatMessages.lire(dechiffre.clair, auteur)
                    val resultats = depot.appliquer(evenement)
                    if (evenement is EvenementRecu.NouveauMessage && resultats.all { it is ResultatMessagerie.Fait }) {
                        recus++
                        val cle = evenement.message.conversation to auteur
                        aAccuser.getOrPut(cle) { mutableListOf() } += evenement.message.id
                    }
                }
                // Les erreurs du stockage local remontent avant l'acquittement : rien n'est perdu.
                traites += recu.seq
                apres = recu.seq
            }
            if (traites.isNotEmpty()) authentifie { api.acquitter(it, traites) }
        } while (releve.suite)

        // Accusés « reçu », au mieux : s'ils échouent, l'auteur verra « envoyé » jusqu'au « lu ».
        if (aAccuser.isNotEmpty()) {
            val espace = authentifie { api.espace(it) }
            for ((cleAccuse, ids) in aAccuser) {
                val (conversation, auteur) = cleAccuse
                if (auteur == moi) continue
                val contenu = FormatMessages.accuse(nouvelId(), conversation, TypeAccuse.Recu, ids, horloge.instant())
                runCatching { deposer(nouvelId(), listOf(auteur), contenu, espace, changees) }
            }
        }
        return Releve(recus, changees)
    }

    /** Accusé « lu » pour les messages affichés à l'écran (EF-MSG-07). */
    suspend fun accuserLecture(messages: List<Message>) = operation {
        val moi = IdMembre(checkNotNull(compte.lire()).membre)
        val parAuteur = messages.filter { !it.sortant && it.auteur != moi && it.contenu is ContenuMessage.Texte }
            .groupBy { it.conversation to it.auteur }
        if (parAuteur.isEmpty()) return@operation
        val espace = authentifie { api.espace(it) }
        val changees = mutableSetOf<IdMembre>()
        for ((cleAccuse, liste) in parAuteur) {
            val (conversation, auteur) = cleAccuse
            val contenu = FormatMessages.accuse(nouvelId(), conversation, TypeAccuse.Lu, liste.map { it.id }, horloge.instant())
            deposer(nouvelId(), listOf(auteur), contenu, espace, changees)
        }
    }

    // Authentification

    /** Exécute [appel] avec un jeton valable ; s'il est refusé, en redemande un et réessaie une fois. */
    private suspend fun <T> authentifie(appel: suspend (String) -> T): T = try {
        appel(jetonValable())
    } catch (e: ErreurReseau.Http) {
        if (e.statut != 401) throw e
        jeton = null
        appel(jetonValable())
    }

    private suspend fun jetonValable(): String {
        jeton?.let { (valeur, expire) -> if (expire - MARGE_JETON > horloge.millis()) return valeur }
        val appareil = checkNotNull(compte.lire()) { "Téléphone pas encore inscrit" }.appareil
        val defi = api.defi(appareil).defi
        val signature = withContext(contexte) { cle.signer(ContenuSigne.pour(appareil, defi)) }
        val nouveau = api.jeton(DemandeJeton(appareil, defi, signature))
        jeton = nouveau.jeton to nouveau.expireLe
        return nouveau.jeton
    }

    private suspend fun <T> operation(bloc: suspend () -> T): T = verrou.withLock { bloc() }

    /**
     * Chaque téléphone est une adresse Signal distincte, nommée par son identifiant serveur : un
     * nouveau téléphone n'hérite jamais de la session de l'ancien.
     */
    private fun adresse(appareil: String, numero: Int) = AdresseAppareil(appareil, numero)

    /**
     * Retient les appareils vus pour [membre]. Un appareil nouveau pour un membre déjà connu,
     * c'est un téléphone remplacé : le code de sécurité a changé (EF-CPT-09).
     */
    private suspend fun noterAppareils(
        membre: IdMembre,
        ids: Collection<String>,
        remplacer: Boolean,
        changees: MutableSet<IdMembre>,
    ) {
        if (ids.isEmpty()) return
        val connus = compte.appareilsConnus()
        val anciens = connus[membre.valeur].orEmpty()
        val nouveaux = ids.toSet() - anciens
        if (anciens.isNotEmpty() && nouveaux.isNotEmpty()) changees += membre
        val apres = if (remplacer) ids.toSet() else anciens + ids
        if (apres != anciens) compte.ecrireAppareilsConnus(connus + (membre.valeur to apres))
    }

    private fun nouvelId() = IdMessage(UUID.randomUUID().toString())

    companion object {
        private const val LOT_RELEVE = 100
        private const val MARGE_JETON = 60_000L
        private val aleatoire = SecureRandom()

        private fun nouveauSecret() = ByteArray(32).also(aleatoire::nextBytes)
        private fun sha256(octets: ByteArray) = MessageDigest.getInstance("SHA-256").digest(octets)
    }
}

private fun PreClePublique.versApi() = PreCleApi(id, cle, signature)

private fun PreCleApi.versChiffrement() = PreClePublique(id, cle, signature)

private fun PaquetApi.versChiffrement() = PaquetDestinataire(
    idEnregistrement = idEnregistrement,
    appareil = numero,
    identite = identite,
    preCleSignee = preCleSignee.versChiffrement(),
    preCleKyber = preCleKyber.versChiffrement(),
    preCleUnique = preCleUnique?.versChiffrement(),
)

private fun TypeEnveloppe.versApi() = when (this) {
    TypeEnveloppe.OuvertureSession -> TypeEnveloppeApi.OuvertureSession
    TypeEnveloppe.Message -> TypeEnveloppeApi.Message
}

private fun TypeEnveloppeApi.versChiffrement() = when (this) {
    TypeEnveloppeApi.OuvertureSession -> TypeEnveloppe.OuvertureSession
    TypeEnveloppeApi.Message -> TypeEnveloppe.Message
}
