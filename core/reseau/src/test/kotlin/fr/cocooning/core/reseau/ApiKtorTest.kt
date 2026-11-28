package fr.cocooning.core.reseau

import fr.cocooning.protocole.api.CheminsApi
import fr.cocooning.protocole.api.EnvoiAppareil
import fr.cocooning.protocole.api.EnvoiMessage
import fr.cocooning.protocole.api.TypeEnveloppeApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ApiKtorTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun api(moteur: MockEngine) = ApiKtor(
        HttpClient(moteur) {
            expectSuccess = false
            install(ContentNegotiation) { json(jsonApi) }
            defaultRequest { url("https://cocooning.example/") }
        },
    )

    @Test
    fun `requete authentifiee, octets en Base64, reponse lue`() = runBlocking {
        var corps = ""
        var autorisation = ""
        val moteur = MockEngine { requete ->
            assertEquals(CheminsApi.MESSAGES, requete.url.encodedPath)
            autorisation = requete.headers[HttpHeaders.Authorization].orEmpty()
            corps = String(requete.body.toByteArray())
            respond("""{"id":"m-1","deposeLe":42,"champFutur":true}""", HttpStatusCode.Created, json)
        }
        val accuse = api(moteur).envoyer("jeton", EnvoiMessage("m-1", listOf(EnvoiAppareil("a", TypeEnveloppeApi.Message, byteArrayOf(1, 2, 3)))))
        assertEquals(42, accuse.deposeLe)
        assertEquals("Bearer jeton", autorisation)
        assertTrue(corps, corps.contains("\"contenu\":\"AQID\""))
    }

    @Test
    fun `erreur du serveur avec son code stable`() = runBlocking {
        val moteur = MockEngine { respond("""{"code":"appareils_incoherents","message":"x","appareils":{"manquants":["b"],"enTrop":[]}}""", HttpStatusCode.Conflict, json) }
        val erreur = assertThrows(ErreurReseau.Http::class.java) {
            runBlocking { api(moteur).envoyer("j", EnvoiMessage("m", emptyList())) }
        }
        assertEquals(409, erreur.statut)
        assertEquals(listOf("b"), erreur.erreur!!.appareils!!.manquants)
    }

    @Test
    fun `pas de reseau devient HorsLigne`() {
        val moteur = MockEngine { throw IOException("Réseau coupé") }
        assertThrows(ErreurReseau.HorsLigne::class.java) { runBlocking { api(moteur).defi("a") } }
    }
}
