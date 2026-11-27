package fr.cocooning.serveur

import fr.cocooning.protocole.api.Acquittement
import fr.cocooning.protocole.api.CheminsApi
import fr.cocooning.protocole.api.ContenuSigne
import fr.cocooning.protocole.api.CreationEspace
import fr.cocooning.protocole.api.Defi
import fr.cocooning.protocole.api.DemandeDefi
import fr.cocooning.protocole.api.DemandeJeton
import fr.cocooning.protocole.api.EnvoiAppareil
import fr.cocooning.protocole.api.EnvoiMessage
import fr.cocooning.protocole.api.EspaceApi
import fr.cocooning.protocole.api.EtatCles
import fr.cocooning.protocole.api.Inscription
import fr.cocooning.protocole.api.Jeton
import fr.cocooning.protocole.api.PaquetsMembre
import fr.cocooning.protocole.api.PreCleApi
import fr.cocooning.protocole.api.PublicationClesApi
import fr.cocooning.protocole.api.RejoindreEspace
import fr.cocooning.protocole.api.Releve
import fr.cocooning.protocole.api.RemplacerAppareil
import fr.cocooning.protocole.api.TypeEnveloppeApi
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.Assert.assertEquals
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Un téléphone simulé : il fait ce que fera l'appli, avec une clé EC P-256 (le Keystore sur le
 * vrai téléphone) et des « messages chiffrés » qui ne sont que des octets, puisque le serveur ne
 * doit jamais rien en comprendre.
 */
class ClientSimule(private val http: HttpClient, val nom: String) {
    var cles: KeyPair = nouvelleCle()
    val secret: ByteArray = octetsAleatoires(32)
    lateinit var inscription: Inscription
    lateinit var jeton: String

    val appareil get() = inscription.appareil
    val membre get() = inscription.membre

    suspend fun creerEspace(): Inscription {
        val reponse = http.post(CheminsApi.ESPACES) {
            json(CreationEspace(cles.public.encoded, sha256(secret)))
        }
        assertEquals(HttpStatusCode.Created, reponse.status)
        return reponse.body<Inscription>().also { inscription = it }
    }

    suspend fun rejoindre(code: String): HttpResponse =
        http.post(CheminsApi.REJOINDRE) { json(RejoindreEspace(code, cles.public.encoded, sha256(secret))) }
            .also { if (it.status == HttpStatusCode.Created) inscription = it.body() }

    suspend fun remplacerTelephone(secretDonne: ByteArray = secret): HttpResponse {
        cles = nouvelleCle()
        return http.post(CheminsApi.REMPLACER_APPAREIL) {
            json(RemplacerAppareil(membre, secretDonne, cles.public.encoded, sha256(secret)))
        }.also { if (it.status == HttpStatusCode.Created) inscription = it.body() }
    }

    suspend fun demanderDefi(): Defi = http.post(CheminsApi.DEFI) { json(DemandeDefi(appareil)) }.body()

    fun signer(defi: ByteArray, cle: KeyPair = cles): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(cle.private)
        update(ContenuSigne.pour(appareil, defi))
        sign()
    }

    suspend fun demanderJeton(defi: ByteArray, signature: ByteArray): HttpResponse =
        http.post(CheminsApi.JETON) { json(DemandeJeton(appareil, defi, signature)) }

    suspend fun authentifier(): String {
        val defi = demanderDefi().defi
        val reponse = demanderJeton(defi, signer(defi))
        assertEquals(HttpStatusCode.OK, reponse.status)
        return reponse.body<Jeton>().jeton.also { jeton = it }
    }

    /** Clés publiques factices, aux tailles de libsignal (identité 33, Kyber 1569, signatures 64). */
    suspend fun publierCles(uniques: IntRange = 1..100): EtatCles {
        val reponse = http.put(CheminsApi.CLES) {
            bearerAuth(jeton)
            json(
                PublicationClesApi(
                    idEnregistrement = 4242,
                    identite = identite,
                    preCleSignee = PreCleApi(1, octetsAleatoires(33), octetsAleatoires(64)),
                    preCleKyber = PreCleApi(1, octetsAleatoires(1569), octetsAleatoires(64)),
                    preClesUniques = uniques.map { PreCleApi(it, octetsAleatoires(33)) },
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, reponse.status)
        return reponse.body()
    }

    val identite: ByteArray by lazy { octetsAleatoires(33) }

    suspend fun etatCles(): EtatCles = http.get(CheminsApi.ETAT_CLES) { bearerAuth(jeton) }.body()

    suspend fun paquets(de: String): HttpResponse = http.get(CheminsApi.clesMembre(de)) { bearerAuth(jeton) }

    suspend fun espace(): EspaceApi = http.get(CheminsApi.ESPACE) { bearerAuth(jeton) }.body()

    suspend fun envoyer(id: String, vararg envois: Pair<String, ByteArray>): HttpResponse =
        http.post(CheminsApi.MESSAGES) {
            bearerAuth(jeton)
            json(EnvoiMessage(id, envois.map { (appareil, octets) -> EnvoiAppareil(appareil, TypeEnveloppeApi.Message, octets) }))
        }

    suspend fun relever(apres: Long = 0, limite: Int = 100): Releve =
        http.get("${CheminsApi.MESSAGES}?apres=$apres&limite=$limite") { bearerAuth(jeton) }.body()

    suspend fun acquitter(seqs: List<Long>): HttpResponse =
        http.post(CheminsApi.ACQUITTER) {
            bearerAuth(jeton)
            json(Acquittement(seqs))
        }

    suspend fun paquetsMembre(de: String): PaquetsMembre = paquets(de).body()

    private fun nouvelleCle(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
}

inline fun <reified T> io.ktor.client.request.HttpRequestBuilder.json(corps: T) {
    contentType(ContentType.Application.Json)
    setBody(corps)
}
