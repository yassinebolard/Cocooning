package fr.cocooning.serveur

import fr.cocooning.protocole.api.Acquittement
import fr.cocooning.protocole.api.AppareilsIncoherents
import fr.cocooning.protocole.api.CheminsApi
import fr.cocooning.protocole.api.CreationEspace
import fr.cocooning.protocole.api.CreationMedia
import fr.cocooning.protocole.api.Defi
import fr.cocooning.protocole.api.DemandeDefi
import fr.cocooning.protocole.api.DemandeJeton
import fr.cocooning.protocole.api.EnvoiMessage
import fr.cocooning.protocole.api.ErreurApi
import fr.cocooning.protocole.api.Jeton
import fr.cocooning.protocole.api.PublicationClesApi
import fr.cocooning.protocole.api.RejoindreEspace
import fr.cocooning.protocole.api.RemplacerAppareil
import fr.cocooning.protocole.api.ReveilApi
import fr.cocooning.protocole.api.TrameTempsReel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.readRemaining
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.io.readByteArray
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toKotlinDuration

/** Format JSON commun : champs inconnus ignorés (compatibilité avec une appli plus récente). */
val jsonCocooning = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "trame"
}

/** Tout ce dont les routes ont besoin, créé une fois au démarrage. */
class Services(
    val config: ConfigServeur,
    val base: Base,
    val horloge: Clock = Clock.systemUTC(),
    reveilleur: Reveilleur? = null,
) {
    val auth = Authentification(config, horloge)
    val espaces = DepotEspaces(base, config, horloge)
    val cles = DepotCles(base, config)
    val messages = DepotMessages(base, config, horloge)
    val medias = DepotMedias(base, config, horloge)
    val tempsReel = TempsReel(jsonCocooning)
    private val client by lazy { HttpClient(CIO) }
    val reveilleur: Reveilleur = reveilleur ?: ReveilleurNtfy(config, client)

    /** Purge périodique : messages et médias expirés, jetons et défis périmés. */
    suspend fun purger() {
        auth.purger()
        messages.purger()
        medias.purger()
        base.tx { executer("DELETE FROM invitations WHERE expire_le < ?", horloge.millis()) }
    }

    fun fermer() {
        if (reveilleur is ReveilleurNtfy) client.close()
        base.close()
    }
}

private val PUBLIC = RateLimitName("public")
private val INSCRIPTION = RateLimitName("inscription")
private val APPAREIL = RateLimitName("appareil")

/** Le serveur Cocooning : greffons Ktor et routes de l'API v1 (ADR 0004). */
fun Application.cocooning(services: Services) {
    val config = services.config
    val journal = LoggerFactory.getLogger("fr.cocooning.serveur")

    if (config.derriereProxy) install(XForwardedHeaders)
    install(CallLogging) { level = Level.INFO }
    install(ContentNegotiation) { json(jsonCocooning) }
    install(WebSockets) {
        pingPeriod = config.intervallePing.toKotlinDuration()
        maxFrameSize = (config.ephemereTailleMax * 2L).coerceAtLeast(16 * 1024)
    }
    install(StatusPages) {
        exception<ErreurServeur> { call, e ->
            call.respond(e.statut, ErreurApi(e.code, e.message.orEmpty(), e.detail as? AppareilsIncoherents))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErreurApi("requete_invalide", "Requête illisible"))
        }
        exception<SerializationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErreurApi("requete_invalide", "Requête illisible"))
        }
        exception<Throwable> { call, e ->
            journal.error("Erreur inattendue", e)
            call.respond(HttpStatusCode.InternalServerError, ErreurApi("erreur_interne", "Erreur du serveur"))
        }
    }
    install(Authentication) {
        bearer("appareil") { authenticate { services.auth.verifier(it.token) } }
    }
    install(RateLimit) {
        register(PUBLIC) {
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
            requestKey { it.request.origin.remoteHost }
        }
        register(INSCRIPTION) {
            rateLimiter(limit = config.inscriptionsParHeure, refillPeriod = 1.hours)
            requestKey { it.request.origin.remoteHost }
        }
        register(APPAREIL) {
            rateLimiter(limit = config.requetesParMinute, refillPeriod = 1.minutes)
            requestKey { it.principal<Appelant>()?.appareil ?: it.request.origin.remoteHost }
        }
    }

    routing {
        rateLimit(INSCRIPTION) { routesInscription(services) }
        rateLimit(PUBLIC) { routesAuthentification(services) }
        authenticate("appareil") {
            rateLimit(APPAREIL) { routesAppareil(services) }
        }
    }

    val purge = launch {
        while (isActive) {
            delay(config.intervallePurge.toKotlinDuration())
            runCatching { services.purger() }.onFailure { journal.error("Purge impossible", it) }
        }
    }
    monitor.subscribe(ApplicationStopped) { purge.cancel() }
}

private fun Route.routesInscription(services: Services) {
    post(CheminsApi.ESPACES) {
        val demande = call.receive<CreationEspace>()
        call.respond(HttpStatusCode.Created, services.espaces.creerEspace(demande.cleAuth, demande.empreinteRecuperation))
    }
    post(CheminsApi.REJOINDRE) {
        val demande = call.receive<RejoindreEspace>()
        call.respond(HttpStatusCode.Created, services.espaces.rejoindre(demande.code, demande.cleAuth, demande.empreinteRecuperation))
    }
    post(CheminsApi.REMPLACER_APPAREIL) {
        val demande = call.receive<RemplacerAppareil>()
        val (inscription, anciens) = services.espaces.remplacer(
            demande.membre, demande.secretRecuperation, demande.cleAuth, demande.empreinteRecuperation,
        )
        anciens.forEach {
            services.auth.revoquer(it)
            services.tempsReel.fermer(it)
        }
        call.respond(HttpStatusCode.Created, inscription)
    }
}

private fun Route.routesAuthentification(services: Services) {
    post(CheminsApi.DEFI) {
        val demande = call.receive<DemandeDefi>()
        // Un défi est donné même pour un appareil inconnu, pour ne pas révéler lesquels existent.
        val (defi, expire) = services.auth.nouveauDefi(demande.appareil)
        call.respond(Defi(defi, expire))
    }
    post(CheminsApi.JETON) {
        val demande = call.receive<DemandeJeton>()
        val appareil = services.espaces.appareil(demande.appareil)
            ?: throw ErreurServeur(HttpStatusCode.Unauthorized, "signature_invalide", "Signature refusée")
        val (jeton, expire) = services.auth.delivrerJeton(appareil.appelant, appareil.cleAuth, demande.defi, demande.signature)
        call.respond(Jeton(jeton, expire))
    }
}

private fun ApplicationCall.appelant(): Appelant = checkNotNull(principal<Appelant>())

private fun Route.routesAppareil(services: Services) {
    get(CheminsApi.ESPACE) { call.respond(services.espaces.espace(call.appelant().espace)) }
    post(CheminsApi.INVITATIONS) { call.respond(HttpStatusCode.Created, services.espaces.inviter(call.appelant())) }
    put(CheminsApi.REVEIL) {
        services.espaces.changerReveil(call.appelant(), call.receive<ReveilApi>())
        call.respond(HttpStatusCode.NoContent)
    }

    put(CheminsApi.CLES) { call.respond(services.cles.publier(call.appelant(), call.receive<PublicationClesApi>())) }
    get(CheminsApi.ETAT_CLES) { call.respond(services.cles.etat(call.appelant())) }
    get("/v1/membres/{membre}/cles") {
        call.respond(services.cles.paquets(call.appelant(), call.parameters["membre"].orEmpty()))
    }

    post(CheminsApi.MESSAGES) {
        val appelant = call.appelant()
        val (accuse, destinataires) = services.messages.deposer(appelant, call.receive<EnvoiMessage>())
        call.respond(HttpStatusCode.Created, accuse)
        destinataires.forEach { appareil ->
            val enLigne = services.tempsReel.envoyer(call.application, appareil, TrameTempsReel.Nouveaux)
            if (!enLigne) services.espaces.appareil(appareil)?.let { services.reveilleur.reveiller(it.reveil) }
        }
    }
    get(CheminsApi.MESSAGES) {
        val apres = call.request.queryParameters["apres"]?.toLongOrNull() ?: 0
        val limite = call.request.queryParameters["limite"]?.toIntOrNull() ?: services.config.releveMax
        call.respond(services.messages.relever(call.appelant(), apres, limite))
    }
    post(CheminsApi.ACQUITTER) {
        services.messages.acquitter(call.appelant(), call.receive<Acquittement>().seqs)
        call.respond(HttpStatusCode.NoContent)
    }

    post(CheminsApi.MEDIAS) {
        call.respond(HttpStatusCode.Created, services.medias.creer(call.appelant(), call.receive<CreationMedia>().taille))
    }
    get("/v1/medias/{id}") { call.respond(services.medias.etat(call.appelant(), call.parameters["id"].orEmpty())) }
    put("/v1/medias/{id}/morceaux/{numero}") {
        val appelant = call.appelant()
        val id = call.parameters["id"].orEmpty()
        val numero = call.parameters["numero"]?.toIntOrNull() ?: throw BadRequestException("Numéro")
        val attendu = services.medias.tailleAttendue(appelant, id, numero)
        // Jamais plus d'un octet de trop en mémoire, quoi qu'annonce le client.
        val octets = call.receiveChannel().readRemaining(attendu + 1L).readByteArray()
        call.respond(services.medias.ecrireMorceau(appelant, id, numero, octets))
    }
    get("/v1/medias/{id}/morceaux/{numero}") {
        val numero = call.parameters["numero"]?.toIntOrNull() ?: throw BadRequestException("Numéro")
        call.respondFile(services.medias.fichierMorceau(call.appelant(), call.parameters["id"].orEmpty(), numero))
    }
    post("/v1/medias/{id}/terminer") {
        call.respond(services.medias.terminer(call.appelant(), call.parameters["id"].orEmpty()))
    }
    post("/v1/medias/{id}/recu") {
        services.medias.marquerRecu(call.appelant(), call.parameters["id"].orEmpty())
        call.respond(HttpStatusCode.NoContent)
    }

    webSocket(CheminsApi.TEMPS_REEL) {
        val appelant = call.appelant()
        services.tempsReel.ajouter(appelant.appareil, this)
        try {
            for (trame in incoming) {
                if (trame !is Frame.Text) continue
                val ephemere = runCatching {
                    jsonCocooning.decodeFromString(TrameTempsReel.serializer(), trame.readText())
                }.getOrNull() as? TrameTempsReel.Ephemere ?: continue
                if (ephemere.contenu.size > services.config.ephemereTailleMax) continue
                val destinataire = services.espaces.appareil(ephemere.appareil) ?: continue
                if (destinataire.appelant.espace != appelant.espace || destinataire.appelant.appareil == appelant.appareil) continue
                services.tempsReel.envoyer(
                    call.application,
                    ephemere.appareil,
                    TrameTempsReel.Ephemere(appelant.appareil, ephemere.contenu, ephemere.type),
                )
            }
        } finally {
            services.tempsReel.retirer(appelant.appareil, this)
        }
    }
}
