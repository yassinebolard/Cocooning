package fr.cocooning.core.reseau

import fr.cocooning.core.chiffrement.MoteurSignal
import fr.cocooning.core.chiffrement.StockageSignalMemoire
import fr.cocooning.core.messagerie.domaine.ContenuMessage
import fr.cocooning.core.messagerie.domaine.EtatEnvoi
import fr.cocooning.core.messagerie.domaine.IdMembre
import fr.cocooning.core.messagerie.domaine.Message
import fr.cocooning.core.messagerie.domaine.ReglesMessages
import fr.cocooning.core.messagerie.domaine.StatutMessage
import fr.cocooning.core.messagerie.donnees.MessagerieRepositoryLocal
import fr.cocooning.core.messagerie.donnees.ResultatMessagerie
import fr.cocooning.core.messagerie.donnees.SourceMessagesMemoire
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deux téléphones complets (dépôt local, vrai libsignal, clé d'appareil, synchroniseur) qui
 * s'écrivent à travers un serveur en mémoire. Le serveur ne voit passer que des octets chiffrés.
 */
class SynchroniseurTest {

    private val serveur = FauxServeur()

    private inner class Telephone(val compte: MagasinCompte = MagasinCompteMemoire()) {
        val cle = CleAppareilMemoire()
        val moteur = MoteurSignal(StockageSignalMemoire())
        private var depotCree: MessagerieRepositoryLocal? = null
        val depot: MessagerieRepositoryLocal
            get() = depotCree ?: runBlocking {
                MessagerieRepositoryLocal(SourceMessagesMemoire(), IdMembre(compte.lire()!!.membre))
            }.also { depotCree = it }
        val synchro = Synchroniseur(serveur, cle, compte, moteur, { depot }, contexte = Dispatchers.Unconfined)

        suspend fun membre() = IdMembre(compte.lire()!!.membre)
        suspend fun appareil() = compte.lire()!!.appareil

        suspend fun ecrire(a: Telephone, texte: String, reponseA: Message? = null): Message =
            (depot.ecrire(synchro.conversation()!!, listOf(a.membre()), texte, reponseA?.id) as ResultatMessagerie.Fait).message

        suspend fun messages() = depot.conversation(synchro.conversation()!!).first()
    }

    private suspend fun couple(): Pair<Telephone, Telephone> {
        val yassine = Telephone()
        val mymy = Telephone()
        val code = yassine.synchro.creerEspace().code
        mymy.synchro.rejoindre(code)
        return yassine to mymy
    }

    @Test
    fun `conversation chiffree de bout en bout avec accuses recu et lu`() = runBlocking {
        val (yassine, mymy) = couple()
        assertEquals(yassine.synchro.conversation(), mymy.synchro.conversation())
        assertEquals(100, serveur.preClesRestantes(mymy.appareil()))

        val question = yassine.ecrire(mymy, "Tu rentres à quelle heure ?")
        assertEquals(1, yassine.synchro.synchroniser().envoyes)
        assertEquals(99, serveur.preClesRestantes(mymy.appareil()))
        assertEquals(EtatEnvoi.Envoye, yassine.messages().single().etatEnvoi)

        assertEquals(1, mymy.synchro.synchroniser().recus)
        val recue = mymy.messages().single()
        assertEquals(ContenuMessage.Texte("Tu rentres à quelle heure ?"), recue.contenu)
        assertEquals(yassine.membre(), recue.auteur)
        assertEquals(question.id, recue.id)

        // L'accusé « reçu » envoyé par Mymy arrive chez Yassine.
        yassine.synchro.synchroniser()
        assertEquals(StatutMessage.Recu, ReglesMessages.statut(yassine.messages().single()))

        // Mymy affiche la conversation : accusé « lu ».
        mymy.synchro.accuserLecture(mymy.messages())
        yassine.synchro.synchroniser()
        assertEquals(StatutMessage.Lu, ReglesMessages.statut(yassine.messages().single()))

        // Réponse citée, dans l'autre sens.
        mymy.ecrire(yassine, "Vers 19 h", reponseA = recue)
        mymy.synchro.synchroniser()
        yassine.synchro.synchroniser()
        val reponse = yassine.messages().single { !it.sortant }
        assertEquals(ContenuMessage.Texte("Vers 19 h"), reponse.contenu)
        assertEquals(question.id, reponse.reponseA)
        assertTrue("Plus rien en attente", serveur.enBoite(yassine.appareil()) == 0 && serveur.enBoite(mymy.appareil()) <= 1)
    }

    @Test
    fun `hors ligne, le message attend puis part une seule fois`() = runBlocking {
        val (yassine, mymy) = couple()
        yassine.ecrire(mymy, "Écrit dans le métro")
        serveur.horsLigne = true
        assertThrows(ErreurReseau.HorsLigne::class.java) { runBlocking { yassine.synchro.synchroniser() } }
        assertEquals(EtatEnvoi.EnAttente, yassine.messages().single().etatEnvoi)

        serveur.horsLigne = false
        assertEquals(1, yassine.synchro.synchroniser().envoyes)
        assertEquals(0, yassine.synchro.synchroniser().envoyes)
        assertEquals(1, mymy.synchro.synchroniser().recus)
        assertEquals(1, mymy.messages().size)
    }

    @Test
    fun `jeton perdu par le serveur, nouvelle authentification sans rien demander`() = runBlocking {
        val (yassine, mymy) = couple()
        yassine.ecrire(mymy, "Coucou")
        serveur.oublierJetons()
        assertEquals(1, yassine.synchro.synchroniser().envoyes)
    }

    @Test
    fun `nouveau telephone de Mymy restaure depuis la sauvegarde`() = runBlocking {
        val (yassine, mymy) = couple()
        yassine.ecrire(mymy, "Avant")
        yassine.synchro.synchroniser()
        mymy.synchro.synchroniser()
        val ancienAppareil = mymy.appareil()

        // La sauvegarde rapporte le compte et son secret, mais ni la clé d'appareil ni les clés Signal.
        val restaure = Telephone(MagasinCompteMemoire(mymy.compte.lire()))
        assertTrue(restaure.synchro.doitReprendre())
        restaure.synchro.reprendreApresRestauration()
        assertTrue(restaure.appareil() != ancienAppareil)
        assertTrue(!restaure.synchro.doitReprendre())

        yassine.ecrire(restaure, "Après")
        val rapport = yassine.synchro.synchroniser()
        assertEquals(1, rapport.envoyes)
        assertEquals(setOf(restaure.membre()), rapport.identitesChangees)

        restaure.synchro.synchroniser()
        assertEquals(ContenuMessage.Texte("Après"), restaure.messages().single().contenu)

        // L'ancien secret ne sert plus : un vol de l'ancienne sauvegarde ne permet pas de reprendre le compte.
        val voleur = Telephone(MagasinCompteMemoire(mymy.compte.lire()))
        val refus = assertThrows(ErreurReseau.Http::class.java) { runBlocking { voleur.synchro.reprendreApresRestauration() } }
        assertEquals(403, refus.statut)
    }

    @Test
    fun `code d'invitation faux refuse`() = runBlocking {
        Telephone().synchro.creerEspace()
        val erreur = assertThrows(ErreurReseau.Http::class.java) { runBlocking { Telephone().synchro.rejoindre("FAUX") } }
        assertEquals(404, erreur.statut)
    }
}
