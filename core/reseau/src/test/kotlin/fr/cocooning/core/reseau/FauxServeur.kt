package fr.cocooning.core.reseau

import fr.cocooning.protocole.api.AccuseDepot
import fr.cocooning.protocole.api.AppareilApi
import fr.cocooning.protocole.api.AppareilsIncoherents
import fr.cocooning.protocole.api.ContenuSigne
import fr.cocooning.protocole.api.CreationEspace
import fr.cocooning.protocole.api.Defi
import fr.cocooning.protocole.api.DemandeJeton
import fr.cocooning.protocole.api.EnvoiMessage
import fr.cocooning.protocole.api.ErreurApi
import fr.cocooning.protocole.api.EspaceApi
import fr.cocooning.protocole.api.EtatCles
import fr.cocooning.protocole.api.Inscription
import fr.cocooning.protocole.api.Invitation
import fr.cocooning.protocole.api.Jeton
import fr.cocooning.protocole.api.MembreApi
import fr.cocooning.protocole.api.MessageRecu
import fr.cocooning.protocole.api.PaquetApi
import fr.cocooning.protocole.api.PaquetsMembre
import fr.cocooning.protocole.api.PreCleApi
import fr.cocooning.protocole.api.PublicationClesApi
import fr.cocooning.protocole.api.RejoindreEspace
import fr.cocooning.protocole.api.Releve
import fr.cocooning.protocole.api.RemplacerAppareil
import fr.cocooning.protocole.api.ReveilApi
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

/**
 * Serveur en mémoire qui suit les mêmes règles que `:backend` (vérifiées là-bas par ses tests
 * d'intégration) : signature des défis, pré-clés uniques données une fois, couverture des
 * appareils, boîtes par appareil. Il ne fait que ranger des octets, comme le vrai.
 */
class FauxServeur : ApiCocooning {
    private class Appareil(val id: String, val membre: String, val numero: Int, val cle: ByteArray, var actif: Boolean = true)

    private var espaceId = ""
    private val membres = mutableMapOf<String, ByteArray>()
    private val appareils = mutableListOf<Appareil>()
    private val codes = mutableSetOf<String>()
    private val defis = mutableMapOf<String, String>()
    private val jetons = mutableMapOf<String, String>()
    private val publications = mutableMapOf<String, PublicationClesApi>()
    private val uniques = mutableMapOf<String, ArrayDeque<PreCleApi>>()
    private val boites = mutableListOf<Pair<String, MessageRecu>>()
    private var seq = 0L

    /** Si vrai, chaque appel échoue comme sans réseau. */
    var horsLigne = false

    /** Nombre de dépôts acceptés, pour vérifier qu'un renvoi n'en crée pas. */
    val depots = mutableListOf<String>()

    override suspend fun creerEspace(demande: CreationEspace): Inscription = reseau {
        espaceId = UUID.randomUUID().toString()
        val inscription = ajouter(demande.cleAuth, demande.empreinteRecuperation)
        val code = "CODE${codes.size}"
        codes += code
        inscription.copy(invitation = Invitation(code, Long.MAX_VALUE))
    }

    override suspend fun rejoindre(demande: RejoindreEspace): Inscription = reseau {
        if (!codes.remove(demande.code)) erreur(404, "invitation_invalide")
        ajouter(demande.cleAuth, demande.empreinteRecuperation)
    }

    override suspend fun remplacer(demande: RemplacerAppareil): Inscription = reseau {
        val empreinte = membres[demande.membre] ?: erreur(403, "recuperation_refusee")
        if (!MessageDigest.isEqual(empreinte, sha(demande.secretRecuperation))) erreur(403, "recuperation_refusee")
        appareils.filter { it.membre == demande.membre && it.actif }.forEach { ancien ->
            ancien.actif = false
            jetons.values.removeAll { it == ancien.id }
            boites.removeAll { it.first == ancien.id }
        }
        membres[demande.membre] = demande.empreinteRecuperation
        val appareil = Appareil(UUID.randomUUID().toString(), demande.membre, 1, demande.cleAuth)
        appareils += appareil
        Inscription(espaceId, demande.membre, appareil.id, 1)
    }

    override suspend fun defi(appareil: String): Defi = reseau {
        val defi = ByteArray(32).also(java.security.SecureRandom()::nextBytes)
        defis[defi.contentToString()] = appareil
        Defi(defi, Long.MAX_VALUE)
    }

    override suspend fun jeton(demande: DemandeJeton): Jeton = reseau {
        if (defis.remove(demande.defi.contentToString()) != demande.appareil) erreur(401, "defi_invalide")
        val appareil = appareils.firstOrNull { it.id == demande.appareil && it.actif } ?: erreur(401, "signature_invalide")
        val valide = Signature.getInstance("SHA256withECDSA").run {
            initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(appareil.cle)))
            update(ContenuSigne.pour(appareil.id, demande.defi))
            verify(demande.signature)
        }
        if (!valide) erreur(401, "signature_invalide")
        val jeton = UUID.randomUUID().toString()
        jetons[jeton] = appareil.id
        Jeton(jeton, Long.MAX_VALUE)
    }

    /** Fait expirer tous les jetons, comme un redémarrage du serveur. */
    fun oublierJetons() = jetons.clear()

    override suspend fun espace(jeton: String): EspaceApi = authentifie(jeton) {
        EspaceApi(
            espaceId,
            membres.keys.map { m -> MembreApi(m, appareils.filter { it.membre == m && it.actif }.map { AppareilApi(it.id, it.numero) }) },
        )
    }

    override suspend fun inviter(jeton: String): Invitation = authentifie(jeton) { Invitation("CODE${codes.size}".also { codes += it }, 0) }

    override suspend fun reveil(jeton: String, reveil: ReveilApi) = authentifie(jeton) { }

    override suspend fun publierCles(jeton: String, publication: PublicationClesApi): EtatCles = authentifie(jeton) { appareil ->
        publications[appareil] = publication
        val file = uniques.getOrPut(appareil) { ArrayDeque() }
        file.addAll(publication.preClesUniques)
        EtatCles(file.size)
    }

    fun preClesRestantes(appareil: String) = uniques[appareil]?.size ?: 0

    override suspend fun paquets(jeton: String, membre: String): PaquetsMembre = authentifie(jeton) {
        PaquetsMembre(
            membre,
            appareils.filter { it.membre == membre && it.actif && it.id in publications }.map { a ->
                val p = publications.getValue(a.id)
                PaquetApi(a.id, a.numero, p.idEnregistrement, p.identite, p.preCleSignee, p.preCleKyber, uniques[a.id]?.removeFirstOrNull())
            },
        )
    }

    override suspend fun envoyer(jeton: String, envoi: EnvoiMessage): AccuseDepot = authentifie(jeton) { expediteur ->
        val actifs = appareils.filter { it.actif }
        val vises = envoi.envois.map { it.appareil }
        val enTrop = vises.filter { v -> actifs.none { it.id == v } || v == expediteur }
        val membresVises = actifs.filter { it.id in vises }.map { it.membre }.toSet()
        val manquants = actifs.filter { it.membre in membresVises && it.id !in vises && it.id != expediteur }.map { it.id }
        if (enTrop.isNotEmpty() || manquants.isNotEmpty()) {
            throw ErreurReseau.Http(409, ErreurApi("appareils_incoherents", "", AppareilsIncoherents(manquants, enTrop)))
        }
        val auteur = appareils.first { it.id == expediteur }
        if ("$expediteur/${envoi.id}" !in depots) {
            depots += "$expediteur/${envoi.id}"
            envoi.envois.forEach {
                boites += it.appareil to MessageRecu(++seq, envoi.id, auteur.membre, auteur.id, auteur.numero, it.type, it.contenu, 0)
            }
        }
        AccuseDepot(envoi.id, 0)
    }

    override suspend fun relever(jeton: String, apres: Long, limite: Int): Releve = authentifie(jeton) { appareil ->
        val miens = boites.filter { it.first == appareil && it.second.seq > apres }.map { it.second }
        Releve(miens.take(limite), miens.size > limite)
    }

    override suspend fun acquitter(jeton: String, seqs: List<Long>) = authentifie(jeton) { appareil ->
        boites.removeAll { it.first == appareil && it.second.seq in seqs }
        Unit
    }

    fun enBoite(appareil: String) = boites.count { it.first == appareil }

    private fun ajouter(cle: ByteArray, empreinte: ByteArray): Inscription {
        val membre = UUID.randomUUID().toString()
        membres[membre] = empreinte
        val appareil = Appareil(UUID.randomUUID().toString(), membre, 1, cle)
        appareils += appareil
        return Inscription(espaceId, membre, appareil.id, 1)
    }

    private inline fun <T> reseau(bloc: () -> T): T {
        if (horsLigne) throw ErreurReseau.HorsLigne(java.io.IOException("hors ligne"))
        return bloc()
    }

    private inline fun <T> authentifie(jeton: String, bloc: (String) -> T): T = reseau {
        bloc(jetons[jeton] ?: erreur(401, "jeton_invalide"))
    }

    private fun erreur(statut: Int, code: String): Nothing = throw ErreurReseau.Http(statut, ErreurApi(code, code))

    private fun sha(octets: ByteArray) = MessageDigest.getInstance("SHA-256").digest(octets)
}
