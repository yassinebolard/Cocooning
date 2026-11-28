package fr.cocooning.core.reseau

import fr.cocooning.protocole.api.AccuseDepot
import fr.cocooning.protocole.api.Acquittement
import fr.cocooning.protocole.api.CheminsApi
import fr.cocooning.protocole.api.CreationEspace
import fr.cocooning.protocole.api.Defi
import fr.cocooning.protocole.api.DemandeDefi
import fr.cocooning.protocole.api.DemandeJeton
import fr.cocooning.protocole.api.EnvoiMessage
import fr.cocooning.protocole.api.ErreurApi
import fr.cocooning.protocole.api.EspaceApi
import fr.cocooning.protocole.api.EtatCles
import fr.cocooning.protocole.api.Inscription
import fr.cocooning.protocole.api.Invitation
import fr.cocooning.protocole.api.Jeton
import fr.cocooning.protocole.api.PaquetsMembre
import fr.cocooning.protocole.api.PublicationClesApi
import fr.cocooning.protocole.api.RejoindreEspace
import fr.cocooning.protocole.api.Releve
import fr.cocooning.protocole.api.RemplacerAppareil
import fr.cocooning.protocole.api.ReveilApi
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** Échec d'un appel au serveur. */
sealed class ErreurReseau(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Le serveur a répondu par une erreur ; [erreur] porte son code stable quand il l'a donné. */
    class Http(val statut: Int, val erreur: ErreurApi?) : ErreurReseau("HTTP $statut ${erreur?.code.orEmpty()}")

    /** Pas de réseau, serveur injoignable ou réponse coupée : à réessayer plus tard. */
    class HorsLigne(cause: Throwable) : ErreurReseau("Serveur injoignable", cause)
}

/** Appels de l'API v1 (ADR 0004). Les appels authentifiés prennent le jeton en paramètre. */
interface ApiCocooning {
    suspend fun creerEspace(demande: CreationEspace): Inscription
    suspend fun rejoindre(demande: RejoindreEspace): Inscription
    suspend fun remplacer(demande: RemplacerAppareil): Inscription
    suspend fun defi(appareil: String): Defi
    suspend fun jeton(demande: DemandeJeton): Jeton
    suspend fun espace(jeton: String): EspaceApi
    suspend fun inviter(jeton: String): Invitation
    suspend fun reveil(jeton: String, reveil: ReveilApi)
    suspend fun publierCles(jeton: String, publication: PublicationClesApi): EtatCles
    suspend fun paquets(jeton: String, membre: String): PaquetsMembre
    suspend fun envoyer(jeton: String, envoi: EnvoiMessage): AccuseDepot
    suspend fun relever(jeton: String, apres: Long, limite: Int): Releve
    suspend fun acquitter(jeton: String, seqs: List<Long>)
}

/** Même format JSON que le serveur : champs inconnus ignorés, pour qu'il puisse évoluer. */
val jsonApi = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "trame"
}

/**
 * [ApiCocooning] par HTTP avec Ktor. [client] reçoit l'adresse du serveur et le JSON ;
 * l'appli passe un client OkHttp ([creerClient]), les tests un moteur simulé.
 */
class ApiKtor(private val client: HttpClient) : ApiCocooning {

    override suspend fun creerEspace(demande: CreationEspace): Inscription =
        appel { client.post(CheminsApi.ESPACES) { json(demande) } }

    override suspend fun rejoindre(demande: RejoindreEspace): Inscription =
        appel { client.post(CheminsApi.REJOINDRE) { json(demande) } }

    override suspend fun remplacer(demande: RemplacerAppareil): Inscription =
        appel { client.post(CheminsApi.REMPLACER_APPAREIL) { json(demande) } }

    override suspend fun defi(appareil: String): Defi = appel { client.post(CheminsApi.DEFI) { json(DemandeDefi(appareil)) } }

    override suspend fun jeton(demande: DemandeJeton): Jeton = appel { client.post(CheminsApi.JETON) { json(demande) } }

    override suspend fun espace(jeton: String): EspaceApi = appel { client.get(CheminsApi.ESPACE) { bearerAuth(jeton) } }

    override suspend fun inviter(jeton: String): Invitation = appel { client.post(CheminsApi.INVITATIONS) { bearerAuth(jeton) } }

    override suspend fun reveil(jeton: String, reveil: ReveilApi) =
        appelSansCorps { client.put(CheminsApi.REVEIL) { bearerAuth(jeton); json(reveil) } }

    override suspend fun publierCles(jeton: String, publication: PublicationClesApi): EtatCles =
        appel { client.put(CheminsApi.CLES) { bearerAuth(jeton); json(publication) } }

    override suspend fun paquets(jeton: String, membre: String): PaquetsMembre =
        appel { client.get(CheminsApi.clesMembre(membre)) { bearerAuth(jeton) } }

    override suspend fun envoyer(jeton: String, envoi: EnvoiMessage): AccuseDepot =
        appel { client.post(CheminsApi.MESSAGES) { bearerAuth(jeton); json(envoi) } }

    override suspend fun relever(jeton: String, apres: Long, limite: Int): Releve = appel {
        client.get(CheminsApi.MESSAGES) {
            bearerAuth(jeton)
            parameter("apres", apres)
            parameter("limite", limite)
        }
    }

    override suspend fun acquitter(jeton: String, seqs: List<Long>) =
        appelSansCorps { client.post(CheminsApi.ACQUITTER) { bearerAuth(jeton); json(Acquittement(seqs)) } }

    private suspend inline fun <reified T> appel(requete: () -> HttpResponse): T = verifier(requete).body()

    private suspend inline fun appelSansCorps(requete: () -> HttpResponse) {
        verifier(requete)
    }

    private suspend inline fun verifier(requete: () -> HttpResponse): HttpResponse {
        val reponse = try {
            requete()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ErreurReseau) {
            throw e
        } catch (e: Exception) {
            throw ErreurReseau.HorsLigne(e)
        }
        if (!reponse.status.isSuccess()) {
            val erreur = runCatching { reponse.body<ErreurApi>() }.getOrNull()
            throw ErreurReseau.Http(reponse.status.value, erreur)
        }
        return reponse
    }

    private inline fun <reified T> HttpRequestBuilder.json(corps: T) {
        contentType(ContentType.Application.Json)
        setBody(corps)
    }

    companion object {
        /** Client de l'appli : OkHttp, TLS du système (ENF-SEC-05, pas d'épinglage), JSON de l'API. */
        fun creerClient(adresse: String): HttpClient = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
            expectSuccess = false
            install(ContentNegotiation) { json(jsonApi) }
            defaultRequest { url(adresse.trimEnd('/') + "/") }
        }
    }
}
