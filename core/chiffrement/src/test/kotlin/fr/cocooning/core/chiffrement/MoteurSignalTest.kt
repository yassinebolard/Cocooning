package fr.cocooning.core.chiffrement

import fr.cocooning.core.chiffrement.ExceptionChiffrement.Raison
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Deux téléphones simulés qui s'écrivent avec le vrai libsignal (bibliothèque native de bureau).
 * Le « serveur » est ici une simple variable : on lui donne la publication, il rend un paquet.
 */
class MoteurSignalTest {

    /** Horloge réglable pour vieillir les pré-clés signées. */
    private class HorlogeReglable(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
    }

    private val horloge = HorlogeReglable(Instant.parse("2026-11-27T19:00:00Z"))

    private class Telephone(val adresse: AdresseAppareil, val stockage: StockageSignal, horloge: Clock) {
        val moteur = MoteurSignal(stockage, adresse.appareil, horloge)
        val publications = mutableListOf<PublicationCles>()

        init {
            moteur.initialiser()
            publier()
        }

        fun publier() {
            moteur.clesAPublier()?.let(publications::add)
        }

        /** Ce que le serveur rendrait : la dernière pré-clé signée et Kyber, et une pré-clé unique pas encore donnée. */
        private var donnees = 0
        fun paquet(avecUnique: Boolean = true): PaquetDestinataire {
            val derniere = publications.last()
            val uniques = publications.flatMap { it.preClesUniques }
            return PaquetDestinataire(
                idEnregistrement = derniere.idEnregistrement,
                appareil = derniere.appareil,
                identite = derniere.identite,
                preCleSignee = derniere.preCleSignee,
                preCleKyber = derniere.preCleKyber,
                preCleUnique = if (avecUnique) uniques.getOrNull(donnees++) else null,
            )
        }
    }

    private fun telephone(membre: String, stockage: StockageSignal = StockageSignalMemoire()) =
        Telephone(AdresseAppareil(membre), stockage, horloge)

    private val yassine = telephone("yassine")
    private val mymy = telephone("mymy")

    private fun Telephone.ecrire(a: Telephone, texte: String) = moteur.chiffrer(a.adresse, texte.toByteArray())

    private fun Telephone.lire(de: Telephone, enveloppe: EnveloppeChiffree): String =
        String(moteur.dechiffrer(de.adresse, enveloppe).clair)

    @Test
    fun `premier message, reponse, puis conversation`() {
        assertEquals(ChangementIdentite.Nouvelle, yassine.moteur.ouvrirSession(mymy.adresse, mymy.paquet()))

        val premier = yassine.ecrire(mymy, "Bonsoir mon cœur")
        assertEquals(TypeEnveloppe.OuvertureSession, premier.type)
        val dechiffre = mymy.moteur.dechiffrer(yassine.adresse, premier)
        assertEquals("Bonsoir mon cœur", String(dechiffre.clair))
        assertEquals(ChangementIdentite.Nouvelle, dechiffre.identite)

        val reponse = mymy.ecrire(yassine, "Coucou 😘")
        assertEquals(TypeEnveloppe.Message, reponse.type)
        assertEquals("Coucou 😘", yassine.lire(mymy, reponse))

        // Une fois la réponse reçue, Yassine n'envoie plus d'ouverture de session.
        val suite = yassine.ecrire(mymy, "Tu rentres quand ?")
        assertEquals(TypeEnveloppe.Message, suite.type)
        assertEquals("Tu rentres quand ?", mymy.lire(yassine, suite))
    }

    @Test
    fun `chaque message a sa propre cle`() {
        yassine.moteur.ouvrirSession(mymy.adresse, mymy.paquet())
        val a = yassine.ecrire(mymy, "Même texte")
        val b = yassine.ecrire(mymy, "Même texte")
        assertFalse(a.octets.contentEquals(b.octets))
    }

    @Test
    fun `les messages arrivent dans le desordre`() {
        yassine.moteur.ouvrirSession(mymy.adresse, mymy.paquet())
        mymy.lire(yassine, yassine.ecrire(mymy, "0"))
        val messages = (1..5).map { yassine.ecrire(mymy, "$it") }
        assertEquals(listOf("4", "1", "5", "3", "2"), listOf(3, 0, 4, 2, 1).map { mymy.lire(yassine, messages[it]) })
    }

    @Test
    fun `un message rejoue est signale comme double`() {
        yassine.moteur.ouvrirSession(mymy.adresse, mymy.paquet())
        mymy.lire(yassine, yassine.ecrire(mymy, "ouverture"))
        yassine.lire(mymy, mymy.ecrire(yassine, "réponse"))
        val trois = yassine.ecrire(mymy, "trois")
        assertEquals(TypeEnveloppe.Message, trois.type)
        mymy.lire(yassine, trois)
        val erreur = assertThrows(ExceptionChiffrement::class.java) { mymy.lire(yassine, trois) }
        assertEquals(Raison.MessageEnDouble, erreur.raison)
    }

    @Test
    fun `une ouverture de session rejouee sans pre-cle unique est refusee`() {
        yassine.moteur.ouvrirSession(mymy.adresse, mymy.paquet(avecUnique = false))
        val ouverture = yassine.ecrire(mymy, "Bonsoir")
        mymy.lire(yassine, ouverture)
        val erreur = assertThrows(ExceptionChiffrement::class.java) { mymy.lire(yassine, ouverture) }
        assertEquals(Raison.MessageEnDouble, erreur.raison)
    }

    @Test
    fun `un message falsifie est refuse et ne casse pas la session`() {
        yassine.moteur.ouvrirSession(mymy.adresse, mymy.paquet())
        mymy.lire(yassine, yassine.ecrire(mymy, "ouverture"))
        yassine.lire(mymy, mymy.ecrire(yassine, "réponse"))
        val vrai = yassine.ecrire(mymy, "Rendez-vous à 20 h")
        assertEquals(TypeEnveloppe.Message, vrai.type)
        // Un octet changé dans le texte chiffré (avant le code d'authentification de 8 octets).
        val faux = vrai.octets.copyOf().also { it[it.size - 20] = (it[it.size - 20] + 1).toByte() }
        val erreur = assertThrows(ExceptionChiffrement::class.java) {
            mymy.moteur.dechiffrer(yassine.adresse, EnveloppeChiffree(vrai.type, faux))
        }
        assertEquals(Raison.MessageInvalide, erreur.raison)
        assertEquals("Rendez-vous à 20 h", mymy.lire(yassine, vrai))
    }

    @Test
    fun `un message pour un autre telephone ne se dechiffre pas`() {
        val leo = telephone("leo")
        yassine.moteur.ouvrirSession(mymy.adresse, mymy.paquet())
        val pourMymy = yassine.ecrire(mymy, "Secret")
        assertThrows(ExceptionChiffrement::class.java) { leo.moteur.dechiffrer(yassine.adresse, pourMymy) }
    }

    @Test
    fun `chiffrer sans session est refuse`() {
        val erreur = assertThrows(ExceptionChiffrement::class.java) { yassine.ecrire(mymy, "Allô ?") }
        assertEquals(Raison.PasDeSession, erreur.raison)
        assertFalse(yassine.moteur.aUneSession(mymy.adresse))
    }

    @Test
    fun `un paquet mal signe est refuse`() {
        val vrai = mymy.paquet()
        val signature = vrai.preCleSignee.signature!!.copyOf().also { it[0] = (it[0] + 1).toByte() }
        val faux = PaquetDestinataire(
            vrai.idEnregistrement, vrai.appareil, vrai.identite,
            PreClePublique(vrai.preCleSignee.id, vrai.preCleSignee.cle, signature),
            vrai.preCleKyber, vrai.preCleUnique,
        )
        val erreur = assertThrows(ExceptionChiffrement::class.java) { yassine.moteur.ouvrirSession(mymy.adresse, faux) }
        assertEquals(Raison.PaquetInvalide, erreur.raison)
        assertFalse(yassine.moteur.aUneSession(mymy.adresse))
    }

    @Test
    fun `la taille chiffree ne trahit pas la longueur exacte du texte`() {
        yassine.moteur.ouvrirSession(mymy.adresse, mymy.paquet())
        mymy.lire(yassine, yassine.ecrire(mymy, "ouverture"))
        mymy.ecrire(yassine, "ok").let { yassine.lire(mymy, it) }
        val court = yassine.ecrire(mymy, "Oui")
        val long = yassine.ecrire(mymy, "Oui, je passe prendre le pain en rentrant")
        assertEquals(court.octets.size, long.octets.size)
    }

    @Test
    fun `nouveau telephone de Mymy - identite changee et code de securite different`() {
        yassine.moteur.ouvrirSession(mymy.adresse, mymy.paquet())
        mymy.lire(yassine, yassine.ecrire(mymy, "Avant"))
        val codeAvant = yassine.moteur.codeSecurite("yassine", "mymy", mymy.moteur.identitePublique())

        // Restauration sur un nouveau téléphone : nouvelle identité (la sauvegarde n'a pas les clés).
        val nouveau = telephone("mymy")
        assertEquals(ChangementIdentite.Changee, yassine.moteur.ouvrirSession(nouveau.adresse, nouveau.paquet()))
        assertEquals("Après", nouveau.lire(yassine, yassine.ecrire(nouveau, "Après")))
        val codeApres = yassine.moteur.codeSecurite("yassine", "mymy", nouveau.moteur.identitePublique())
        assertNotEquals(codeAvant.chiffres, codeApres.chiffres)
    }

    @Test
    fun `l'autre cote voit aussi le changement d'identite au premier message`() {
        yassine.moteur.ouvrirSession(mymy.adresse, mymy.paquet())
        mymy.lire(yassine, yassine.ecrire(mymy, "Avant"))
        val nouveauYassine = telephone("yassine")
        nouveauYassine.moteur.ouvrirSession(mymy.adresse, mymy.paquet())
        val message = mymy.moteur.dechiffrer(yassine.adresse, nouveauYassine.ecrire(mymy, "C'est mon nouveau téléphone"))
        assertEquals(ChangementIdentite.Changee, message.identite)
        assertEquals("C'est mon nouveau téléphone", String(message.clair))
    }

    @Test
    fun `une ouverture falsifiee ne remplace pas l'identite retenue`() {
        yassine.moteur.ouvrirSession(mymy.adresse, mymy.paquet())
        mymy.lire(yassine, yassine.ecrire(mymy, "Avant"))
        val identiteRetenue = mymy.stockage.identiteDistante("yassine", 1)!!

        val imposteur = telephone("imposteur")
        imposteur.moteur.ouvrirSession(mymy.adresse, mymy.paquet())
        val ouverture = imposteur.ecrire(mymy, "Je suis Yassine")
        val abime = ouverture.octets.copyOf().also { it[it.size - 3] = (it[it.size - 3] + 1).toByte() }
        assertThrows(ExceptionChiffrement::class.java) {
            mymy.moteur.dechiffrer(yassine.adresse, EnveloppeChiffree(ouverture.type, abime))
        }
        assertArrayEquals(identiteRetenue, mymy.stockage.identiteDistante("yassine", 1))
    }

    @Test
    fun `code de securite identique des deux cotes et QR croise`() {
        val chezYassine = yassine.moteur.codeSecurite("yassine", "mymy", mymy.moteur.identitePublique())
        val chezMymy = mymy.moteur.codeSecurite("mymy", "yassine", yassine.moteur.identitePublique())
        assertEquals(chezYassine.chiffres, chezMymy.chiffres)
        assertTrue(Regex("(\\d{5} ){11}\\d{5}").matches(chezYassine.chiffres))
        assertTrue(yassine.moteur.qrCorrespond("yassine", "mymy", mymy.moteur.identitePublique(), chezMymy.qr))
        val autre = telephone("mymy")
        assertFalse(yassine.moteur.qrCorrespond("yassine", "mymy", autre.moteur.identitePublique(), chezMymy.qr))
        assertFalse(yassine.moteur.qrCorrespond("yassine", "mymy", mymy.moteur.identitePublique(), byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `les pre-cles uniques se consomment et se rechargent sous le seuil`() {
        val premiere = mymy.publications.single()
        assertEquals(MoteurSignal.PRE_CLES_PAR_LOT, premiere.preClesUniques.size)
        assertNull("Rien de neuf à publier", mymy.moteur.clesAPublier())

        // 81 ouvertures de session consomment 81 pré-clés : il en reste 19, sous le seuil de 20.
        repeat(81) { i ->
            val ami = telephone("ami$i")
            ami.moteur.ouvrirSession(mymy.adresse, mymy.paquet())
            mymy.lire(ami, ami.ecrire(mymy, "Salut"))
        }
        val recharge = mymy.moteur.clesAPublier()
        assertNotNull(recharge)
        assertEquals(81, recharge!!.preClesUniques.size)
        val dejaPubliees = premiere.preClesUniques.map { it.id }.toSet()
        assertTrue(recharge.preClesUniques.none { it.id in dejaPubliees })
        // Pas de renouvellement de la pré-clé signée pour autant.
        assertEquals(premiere.preCleSignee.id, recharge.preCleSignee.id)
        assertNull(mymy.moteur.clesAPublier())
    }

    @Test
    fun `pre-cle signee renouvelee apres 7 jours, anciennes gardees pour les messages en route`() {
        yassine.moteur.ouvrirSession(mymy.adresse, mymy.paquet())
        val enRoute = yassine.ecrire(mymy, "Écrit avant le renouvellement")
        val avant = mymy.publications.last().preCleSignee.id

        horloge.instant = horloge.instant.plus(Duration.ofDays(6))
        assertNull(mymy.moteur.clesAPublier())
        horloge.instant = horloge.instant.plus(Duration.ofDays(1))
        val apres = mymy.moteur.clesAPublier()!!
        assertNotEquals(avant, apres.preCleSignee.id)
        assertNotEquals(mymy.publications.last().preCleKyber.id, apres.preCleKyber.id)
        assertTrue(apres.preClesUniques.isEmpty())

        assertEquals("Écrit avant le renouvellement", mymy.lire(yassine, enRoute))
    }

    @Test
    fun `l'etat survit a la recreation du moteur sur le meme stockage`() {
        yassine.moteur.ouvrirSession(mymy.adresse, mymy.paquet())
        mymy.lire(yassine, yassine.ecrire(mymy, "Avant redémarrage"))
        val identite = mymy.moteur.identitePublique()

        val relance = MoteurSignal(mymy.stockage, horloge = horloge)
        assertFalse(relance.initialiser())
        assertArrayEquals(identite, relance.identitePublique())
        assertEquals("Après redémarrage", String(relance.dechiffrer(yassine.adresse, yassine.ecrire(mymy, "Après redémarrage")).clair))
    }

    @Test
    fun `sans identite, rien ne se fait`() {
        val vierge = MoteurSignal(StockageSignalMemoire())
        val erreur = assertThrows(ExceptionChiffrement::class.java) { vierge.identitePublique() }
        assertEquals(Raison.IdentiteAbsente, erreur.raison)
    }
}
