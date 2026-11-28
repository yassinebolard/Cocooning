package fr.cocooning.serveur

import fr.cocooning.protocole.api.AccuseDepot
import fr.cocooning.protocole.api.CheminsApi
import fr.cocooning.protocole.api.CreationMedia
import fr.cocooning.protocole.api.ErreurApi
import fr.cocooning.protocole.api.EtatMedia
import fr.cocooning.protocole.api.Invitation
import fr.cocooning.protocole.api.MediaCree
import fr.cocooning.protocole.api.ReveilApi
import fr.cocooning.protocole.api.TrameTempsReel
import fr.cocooning.protocole.api.TypeReveil
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Collections

/**
 * Tests d'intégration : le vrai serveur Ktor, sa base SQLite en mémoire, et deux téléphones
 * simulés (Yassine et Mymy) qui passent par l'API HTTP comme le fera l'appli.
 */
class ServeurTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private class HorlogeReglable(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
        fun avancer(duree: Duration) {
            instant = instant.plus(duree)
        }
    }

    private val horloge = HorlogeReglable(Instant.parse("2026-11-27T20:00:00Z"))
    private val reveils: MutableList<ReveilApi> = Collections.synchronizedList(mutableListOf())

    private fun serveur(
        config: ConfigServeur = ConfigServeur(inscriptionsParHeure = 1000, tailleMorceau = 64 * 1024),
        test: suspend ApplicationTestBuilder.(HttpClient, Services) -> Unit,
    ) = testApplication {
        val services = Services(
            config.copy(dossierMedias = dossier.newFolder().path),
            Base.enMemoire(),
            horloge,
            Reveilleur { reveils.add(it) },
        )
        application { cocooning(services) }
        val http = createClient {
            install(ContentNegotiation) { json(jsonCocooning) }
            install(WebSockets)
        }
        test(http, services)
    }

    /** Yassine crée l'espace, Mymy le rejoint, chacun s'authentifie et publie ses clés. */
    private suspend fun couple(http: HttpClient): Pair<ClientSimule, ClientSimule> {
        val yassine = ClientSimule(http, "yassine")
        val mymy = ClientSimule(http, "mymy")
        val invitation = yassine.creerEspace().invitation!!
        assertEquals(HttpStatusCode.Created, mymy.rejoindre(invitation.code).status)
        listOf(yassine, mymy).forEach {
            it.authentifier()
            it.publierCles()
        }
        return yassine to mymy
    }

    @Test
    fun `parcours complet - inscription, cles, message chiffre, releve et acquittement`() = serveur { http, _ ->
        val (yassine, mymy) = couple(http)
        assertEquals(yassine.inscription.espace, mymy.inscription.espace)
        assertEquals(1, mymy.inscription.numero)

        val espace = yassine.espace()
        assertEquals(2, espace.membres.size)
        assertEquals(listOf(mymy.appareil), espace.membres.single { it.id == mymy.membre }.appareils.map { it.id })

        // Yassine récupère le paquet de Mymy : une pré-clé unique part, elle ne sera jamais redonnée.
        val paquet = yassine.paquetsMembre(mymy.membre).paquets.single()
        assertArrayEquals(mymy.identite, paquet.identite)
        assertEquals(1, paquet.preCleUnique!!.id)
        assertEquals(99, mymy.etatCles().preClesUniquesRestantes)
        assertEquals(2, yassine.paquetsMembre(mymy.membre).paquets.single().preCleUnique!!.id)

        val chiffre = octetsAleatoires(180)
        val depot = yassine.envoyer("m-1", mymy.appareil to chiffre)
        assertEquals(HttpStatusCode.Created, depot.status)
        val accuse = depot.body<AccuseDepot>()
        assertEquals(horloge.millis(), accuse.deposeLe)

        // Renvoi après une coupure : même accusé, aucun doublon.
        assertEquals(accuse, yassine.envoyer("m-1", mymy.appareil to chiffre).body<AccuseDepot>())

        val releve = mymy.relever()
        val recu = releve.messages.single()
        assertArrayEquals(chiffre, recu.contenu)
        assertEquals("m-1", recu.id)
        assertEquals(yassine.membre, recu.expediteurMembre)
        assertEquals(yassine.appareil, recu.expediteurAppareil)
        assertEquals(1, recu.expediteurNumero)
        assertFalse(releve.suite)
        assertTrue("Rien pour l'expéditeur", yassine.relever().messages.isEmpty())

        assertEquals(HttpStatusCode.NoContent, mymy.acquitter(listOf(recu.seq)).status)
        assertTrue(mymy.relever().messages.isEmpty())
    }

    @Test
    fun `releve par pages, dans l'ordre d'arrivee`() = serveur { http, _ ->
        val (yassine, mymy) = couple(http)
        repeat(5) { yassine.envoyer("m-$it", mymy.appareil to byteArrayOf(it.toByte())) }
        val page1 = mymy.relever(limite = 3)
        assertEquals(listOf("m-0", "m-1", "m-2"), page1.messages.map { it.id })
        assertTrue(page1.suite)
        val page2 = mymy.relever(apres = page1.messages.last().seq, limite = 3)
        assertEquals(listOf("m-3", "m-4"), page2.messages.map { it.id })
        assertFalse(page2.suite)
    }

    @Test
    fun `on n'acquitte pas les messages d'un autre appareil`() = serveur { http, _ ->
        val (yassine, mymy) = couple(http)
        yassine.envoyer("m-1", mymy.appareil to byteArrayOf(1))
        val seq = mymy.relever().messages.single().seq
        yassine.acquitter(listOf(seq))
        assertEquals(1, mymy.relever().messages.size)
    }

    @Test
    fun `sans jeton, ou avec un jeton expire, tout est refuse`() = serveur { http, _ ->
        val (_, mymy) = couple(http)
        assertEquals(HttpStatusCode.Unauthorized, http.get(CheminsApi.ESPACE).status)
        assertEquals(HttpStatusCode.Unauthorized, http.get(CheminsApi.ESPACE) { bearerAuth("faux") }.status)
        horloge.avancer(Duration.ofMinutes(16))
        assertEquals(HttpStatusCode.Unauthorized, http.get(CheminsApi.ESPACE) { bearerAuth(mymy.jeton) }.status)
        mymy.authentifier()
        assertEquals(HttpStatusCode.OK, http.get(CheminsApi.ESPACE) { bearerAuth(mymy.jeton) }.status)
    }

    @Test
    fun `defi a usage unique, signature d'une autre cle refusee`() = serveur { http, _ ->
        val (yassine, mymy) = couple(http)
        val defi = mymy.demanderDefi().defi
        // Signé par la clé de Yassine : refusé, et le défi est consommé quand même.
        assertEquals(HttpStatusCode.Unauthorized, mymy.demanderJeton(defi, mymy.signer(defi, yassine.cles)).status)
        assertEquals(HttpStatusCode.Unauthorized, mymy.demanderJeton(defi, mymy.signer(defi)).status)

        val autre = mymy.demanderDefi().defi
        assertEquals(HttpStatusCode.OK, mymy.demanderJeton(autre, mymy.signer(autre)).status)
        assertEquals(HttpStatusCode.Unauthorized, mymy.demanderJeton(autre, mymy.signer(autre)).status)

        val expire = mymy.demanderDefi().defi
        horloge.avancer(Duration.ofSeconds(61))
        assertEquals(HttpStatusCode.Unauthorized, mymy.demanderJeton(expire, mymy.signer(expire)).status)
    }

    @Test
    fun `invitation a usage unique, espace limite a deux, code expire`() = serveur { http, _ ->
        val yassine = ClientSimule(http, "yassine")
        val code = yassine.creerEspace().invitation!!.code
        assertEquals(8, code.length)
        assertEquals(HttpStatusCode.Created, ClientSimule(http, "mymy").rejoindre(code.lowercase()).status)
        assertEquals(HttpStatusCode.NotFound, ClientSimule(http, "intrus").rejoindre(code).status)

        yassine.authentifier()
        val nouveau = http.post(CheminsApi.INVITATIONS) { bearerAuth(yassine.jeton) }.body<Invitation>()
        val refus = ClientSimule(http, "troisieme").rejoindre(nouveau.code)
        assertEquals(HttpStatusCode.Conflict, refus.status)
        assertEquals("espace_complet", refus.body<ErreurApi>().code)

        val autre = ClientSimule(http, "autre").creerEspace().invitation!!
        horloge.avancer(Duration.ofHours(25))
        assertEquals(HttpStatusCode.NotFound, ClientSimule(http, "tard").rejoindre(autre.code).status)
    }

    @Test
    fun `cle d'authentification qui n'est pas EC P-256 refusee`() = serveur { http, _ ->
        val rsa = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val reponse = http.post(CheminsApi.ESPACES) {
            json(fr.cocooning.protocole.api.CreationEspace(rsa.public.encoded, ByteArray(32)))
        }
        assertEquals(HttpStatusCode.BadRequest, reponse.status)
        assertEquals("cle_invalide", reponse.body<ErreurApi>().code)
    }

    @Test
    fun `chaque appareil actif du membre vise doit etre servi`() = serveur { http, _ ->
        val (yassine, mymy) = couple(http)
        val aMoi = yassine.envoyer("m-1", yassine.appareil to byteArrayOf(1))
        assertEquals(HttpStatusCode.Conflict, aMoi.status)
        assertEquals(listOf(yassine.appareil), aMoi.body<ErreurApi>().appareils!!.enTrop)

        val inconnu = yassine.envoyer("m-2", "appareil-inconnu" to byteArrayOf(1))
        assertEquals(listOf("appareil-inconnu"), inconnu.body<ErreurApi>().appareils!!.enTrop)

        val enDouble = yassine.envoyer("m-3", mymy.appareil to byteArrayOf(1), mymy.appareil to byteArrayOf(2))
        assertEquals(HttpStatusCode.BadRequest, enDouble.status)
        assertTrue(mymy.relever().messages.isEmpty())
    }

    @Test
    fun `un message trop gros est refuse`() = serveur(ConfigServeur(inscriptionsParHeure = 1000, tailleMaxEnveloppe = 1024)) { http, _ ->
        val (yassine, mymy) = couple(http)
        assertEquals(HttpStatusCode.PayloadTooLarge, yassine.envoyer("m-1", mymy.appareil to ByteArray(1025)).status)
        assertEquals(HttpStatusCode.Created, yassine.envoyer("m-2", mymy.appareil to ByteArray(1024)).status)
    }

    @Test
    fun `deux espaces ne se voient pas`() = serveur { http, _ ->
        val (yassine, mymy) = couple(http)
        val (autre, _) = couple(http)
        assertEquals(HttpStatusCode.NotFound, autre.paquets(mymy.membre).status)
        val intrusion = autre.envoyer("m-1", mymy.appareil to byteArrayOf(1))
        assertEquals(HttpStatusCode.Conflict, intrusion.status)
        assertTrue(mymy.relever().messages.isEmpty())
        assertEquals(2, yassine.espace().membres.size)
    }

    @Test
    fun `l'identite publiee d'un appareil est figee`() = serveur { http, _ ->
        val (yassine, _) = couple(http)
        val reponse = http.put(CheminsApi.CLES) {
            bearerAuth(yassine.jeton)
            json(
                fr.cocooning.protocole.api.PublicationClesApi(
                    1, octetsAleatoires(33),
                    fr.cocooning.protocole.api.PreCleApi(2, octetsAleatoires(33), octetsAleatoires(64)),
                    fr.cocooning.protocole.api.PreCleApi(2, octetsAleatoires(1569), octetsAleatoires(64)),
                    emptyList(),
                ),
            )
        }
        assertEquals(HttpStatusCode.Conflict, reponse.status)
        assertEquals("identite_figee", reponse.body<ErreurApi>().code)
        // Republier la même identité avec de nouvelles pré-clés est permis (recharge).
        assertEquals(150, yassine.publierCles(101..150).preClesUniquesRestantes)
    }

    @Test
    fun `nouveau telephone de Mymy avec son secret de recuperation`() = serveur { http, _ ->
        val (yassine, mymy) = couple(http)
        yassine.envoyer("avant", mymy.appareil to byteArrayOf(1))
        val ancienAppareil = mymy.appareil
        val ancienJeton = mymy.jeton

        assertEquals(HttpStatusCode.Forbidden, mymy.remplacerTelephone(octetsAleatoires(32)).status)
        assertEquals(HttpStatusCode.Created, mymy.remplacerTelephone().status)
        assertTrue(mymy.appareil != ancienAppareil)
        assertEquals("Même numéro : l'autre côté voit l'identité changer", 1, mymy.inscription.numero)

        // L'ancien téléphone est coupé, sa boîte vidée.
        assertEquals(HttpStatusCode.Unauthorized, http.get(CheminsApi.ESPACE) { bearerAuth(ancienJeton) }.status)
        mymy.authentifier()
        assertTrue(mymy.relever().messages.isEmpty())

        // Yassine voit le nouvel appareil, et ne peut plus écrire à l'ancien.
        val appareils = yassine.espace().membres.single { it.id == mymy.membre }.appareils.map { it.id }
        assertEquals(listOf(mymy.appareil), appareils)
        val versAncien = yassine.envoyer("apres-1", ancienAppareil to byteArrayOf(2))
        assertEquals(listOf(ancienAppareil), versAncien.body<ErreurApi>().appareils!!.enTrop)
        assertTrue("Pas de clés publiées pour le nouveau téléphone", yassine.paquetsMembre(mymy.membre).paquets.isEmpty())

        mymy.publierCles()
        assertEquals(HttpStatusCode.Created, yassine.envoyer("apres-2", mymy.appareil to byteArrayOf(3)).status)
        assertEquals(listOf("apres-2"), mymy.relever().messages.map { it.id })
    }

    @Test
    fun `media chiffre envoye par morceaux, repris, telecharge puis purge`() = serveur { http, services ->
        val (yassine, mymy) = couple(http)
        val media = octetsAleatoires(150_000) // 3 morceaux de 64 Kio, le dernier plus court
        val cree = http.post(CheminsApi.MEDIAS) {
            bearerAuth(yassine.jeton)
            json(CreationMedia(media.size.toLong()))
        }.body<MediaCree>()
        assertEquals(3, cree.nombreMorceaux)
        val morceaux = media.toList().chunked(cree.tailleMorceau).map { it.toByteArray() }

        suspend fun envoyer(n: Int, octets: ByteArray) =
            http.put(CheminsApi.morceau(cree.id, n)) { bearerAuth(yassine.jeton); setBody(octets) }

        assertEquals(HttpStatusCode.BadRequest, envoyer(0, morceaux[0].copyOf(100)).status)
        assertEquals(HttpStatusCode.OK, envoyer(0, morceaux[0]).status)
        assertEquals(HttpStatusCode.OK, envoyer(2, morceaux[2]).status)
        // Coupure : terminer est refusé tant qu'il manque un morceau, l'état dit lequel.
        assertEquals(HttpStatusCode.Conflict, http.post(CheminsApi.terminerMedia(cree.id)) { bearerAuth(yassine.jeton) }.status)
        val etat = http.get(CheminsApi.media(cree.id)) { bearerAuth(yassine.jeton) }.body<EtatMedia>()
        assertEquals(listOf(0, 2), etat.morceauxRecus)
        // Seul l'auteur envoie.
        assertEquals(HttpStatusCode.Forbidden, http.put(CheminsApi.morceau(cree.id, 1)) { bearerAuth(mymy.jeton); setBody(morceaux[1]) }.status)
        assertEquals(HttpStatusCode.OK, envoyer(1, morceaux[1]).status)
        assertTrue(http.post(CheminsApi.terminerMedia(cree.id)) { bearerAuth(yassine.jeton) }.body<EtatMedia>().termine)

        val recu = (0 until 3).map { http.get(CheminsApi.morceau(cree.id, it)) { bearerAuth(mymy.jeton) }.readRawBytes() }
        assertArrayEquals(media, recu.reduce { a, b -> a + b })

        // Pas de purge avant 30 jours après la réception par tous.
        assertEquals(HttpStatusCode.NoContent, http.post(CheminsApi.mediaRecu(cree.id)) { bearerAuth(mymy.jeton) }.status)
        horloge.avancer(Duration.ofDays(29))
        services.purger()
        mymy.authentifier()
        assertEquals(HttpStatusCode.OK, http.get(CheminsApi.media(cree.id)) { bearerAuth(mymy.jeton) }.status)
        horloge.avancer(Duration.ofDays(2))
        services.purger()
        mymy.authentifier()
        assertEquals(HttpStatusCode.NotFound, http.get(CheminsApi.media(cree.id)) { bearerAuth(mymy.jeton) }.status)
        assertEquals(0, services.medias.occupe())
    }

    @Test
    fun `media d'un autre espace introuvable, stockage plein refuse`() =
        serveur(ConfigServeur(inscriptionsParHeure = 1000, tailleMorceau = 64 * 1024, quotaMedias = 1_000_000)) { http, _ ->
            val (yassine, _) = couple(http)
            val (autre, _) = couple(http)
            val cree = http.post(CheminsApi.MEDIAS) { bearerAuth(yassine.jeton); json(CreationMedia(900_000)) }.body<MediaCree>()
            assertEquals(HttpStatusCode.NotFound, http.get(CheminsApi.media(cree.id)) { bearerAuth(autre.jeton) }.status)
            // 900 000 + 100 000 > 95 % de 1 000 000.
            val plein = http.post(CheminsApi.MEDIAS) { bearerAuth(yassine.jeton); json(CreationMedia(100_000)) }
            assertEquals(HttpStatusCode.InsufficientStorage, plein.status)
        }

    @Test
    fun `un message jamais releve est efface au bout de 30 jours`() = serveur { http, services ->
        val (yassine, mymy) = couple(http)
        yassine.envoyer("m-1", mymy.appareil to byteArrayOf(1))
        horloge.avancer(Duration.ofDays(31))
        services.purger()
        mymy.authentifier()
        assertTrue(mymy.relever().messages.isEmpty())
    }

    @Test
    fun `temps reel - annonce des nouveaux messages et ephemere relaye`() = serveur { http, services ->
        val (yassine, mymy) = couple(http)
        val nouveaux = CompletableDeferred<String>()
        val ephemere = CompletableDeferred<TrameTempsReel.Ephemere>()
        val pret = CompletableDeferred<Unit>()

        kotlinx.coroutines.coroutineScope {
        val ecoute = launchEcoute(http, mymy, pret, nouveaux, ephemere)
        withTimeout(5_000) {
            pret.await()
            while (!services.tempsReel.connecte(mymy.appareil)) kotlinx.coroutines.delay(10)
        }
        yassine.envoyer("m-1", mymy.appareil to byteArrayOf(1))
        assertTrue(withTimeout(5_000) { nouveaux.await() }.contains("nouveaux"))
        assertTrue("Connectée : pas de réveil", reveils.isEmpty())

        http.webSocket(CheminsApi.TEMPS_REEL, request = { bearerAuth(yassine.jeton) }) {
            val trame = TrameTempsReel.Ephemere(mymy.appareil, byteArrayOf(9, 9))
            send(Frame.Text(jsonCocooning.encodeToString(TrameTempsReel.serializer(), trame)))
            val recue = withTimeout(5_000) { ephemere.await() }
            assertEquals(yassine.appareil, recue.appareil)
            assertArrayEquals(byteArrayOf(9, 9), recue.contenu)
        }
        ecoute.cancel()
        }
    }

    @Test
    fun `hors ligne, l'appareil est reveille sans aucun contenu`() = serveur { http, _ ->
        val (yassine, mymy) = couple(http)
        val reveil = ReveilApi(TypeReveil.Ntfy, "sujet-aleatoire-de-mymy")
        assertEquals(HttpStatusCode.NoContent, http.put(CheminsApi.REVEIL) { bearerAuth(mymy.jeton); json(reveil) }.status)
        yassine.envoyer("m-1", mymy.appareil to byteArrayOf(1))
        assertEquals(listOf(reveil), reveils.toList())
    }

    @Test
    fun `trop d'inscriptions depuis la meme adresse`() = serveur(ConfigServeur(inscriptionsParHeure = 2)) { http, _ ->
        ClientSimule(http, "a").creerEspace()
        ClientSimule(http, "b").creerEspace()
        val reponse = http.post(CheminsApi.ESPACES) {
            json(fr.cocooning.protocole.api.CreationEspace(ByteArray(0), ByteArray(32)))
        }
        assertEquals(HttpStatusCode.TooManyRequests, reponse.status)
    }

    @Test
    fun `le modele de configuration correspond aux valeurs par defaut`() {
        val modele = File(System.getProperty("user.dir")).resolve("../config/server.conf.example")
        assertTrue(modele.absolutePath, modele.isFile)
        assertEquals(ConfigServeur(), ConfigServeur.charger(modele))
    }

    @Test
    fun `une valeur du fichier remplace la valeur par defaut`() {
        val fichier = dossier.newFile("server.conf")
        fichier.writeText("cocooning { ecoute.port = 9090, medias.quota = 1GB, messages.conservation = 7d }")
        val config = ConfigServeur.charger(fichier)
        assertEquals(9090, config.port)
        assertEquals(1_000_000_000, config.quotaMedias)
        assertEquals(Duration.ofDays(7), config.conservationMessages)
        assertEquals(ConfigServeur().validiteJeton, config.validiteJeton)
        assertNull(null)
    }
}

/** Mymy écoute la WebSocket : elle signale quand elle est prête, puis chaque trame reçue. */
private fun kotlinx.coroutines.CoroutineScope.launchEcoute(
    http: HttpClient,
    mymy: ClientSimule,
    pret: CompletableDeferred<Unit>,
    nouveaux: CompletableDeferred<String>,
    ephemere: CompletableDeferred<TrameTempsReel.Ephemere>,
) = launch {
    http.webSocket(CheminsApi.TEMPS_REEL, request = { bearerAuth(mymy.jeton) }) {
        pret.complete(Unit)
        for (trame in incoming) {
            val texte = (trame as? Frame.Text)?.readText() ?: continue
            when (val t = jsonCocooning.decodeFromString(TrameTempsReel.serializer(), texte)) {
                is TrameTempsReel.Nouveaux -> nouveaux.complete(texte)
                is TrameTempsReel.Ephemere -> ephemere.complete(t)
            }
        }
    }
}
