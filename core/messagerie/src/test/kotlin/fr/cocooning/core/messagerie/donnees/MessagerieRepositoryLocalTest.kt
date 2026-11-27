package fr.cocooning.core.messagerie.donnees

import fr.cocooning.core.messagerie.domaine.ContenuMessage
import fr.cocooning.core.messagerie.domaine.ErreurMessagerie
import fr.cocooning.core.messagerie.domaine.EtatEnvoi
import fr.cocooning.core.messagerie.domaine.IdConversation
import fr.cocooning.core.messagerie.domaine.IdMembre
import fr.cocooning.core.messagerie.domaine.IdMessage
import fr.cocooning.core.messagerie.domaine.Message
import fr.cocooning.core.messagerie.domaine.MessageEntrant
import fr.cocooning.core.messagerie.domaine.Reaction
import fr.cocooning.core.messagerie.domaine.ReglesMessages
import fr.cocooning.core.messagerie.domaine.StatutMessage
import fr.cocooning.core.messagerie.domaine.TypeAccuse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MessagerieRepositoryLocalTest {

    private val yassine = IdMembre("yassine")
    private val mymy = IdMembre("mymy")
    private val couple = IdConversation("couple")
    private val t0: Instant = Instant.parse("2026-11-27T19:00:00.123456Z")

    private val source = SourceMessagesMemoire()
    private var compteur = 0
    private val depot = MessagerieRepositoryLocal(
        source = source,
        moi = yassine,
        horloge = Clock.fixed(t0, ZoneOffset.UTC),
        nouvelId = { IdMessage("sortant-${++compteur}") },
    )

    private suspend fun ecrire(texte: String = "Tu rentres à quelle heure ?", reponseA: IdMessage? = null) =
        (depot.ecrire(couple, listOf(mymy), texte, reponseA) as ResultatMessagerie.Fait).message

    private fun entrant(id: String = "entrant-1", texte: String = "Vers 19 h", reponseA: IdMessage? = null) =
        MessageEntrant(IdMessage(id), couple, mymy, texte, reponseA, t0.minusSeconds(30))

    private suspend fun visibles() = depot.conversation(couple).first()

    private fun ResultatMessagerie.message(): Message = (this as ResultatMessagerie.Fait).message

    @Test
    fun `un message ecrit est enregistre tout de suite en attente`() = runTest {
        val message = ecrire("  Coucou  \r\n")
        assertEquals(ContenuMessage.Texte("Coucou"), message.contenu)
        assertEquals(EtatEnvoi.EnAttente, message.etatEnvoi)
        assertEquals(StatutMessage.EnAttente, ReglesMessages.statut(message))
        // Heure gardée à la milliseconde, comme en base.
        assertEquals(Instant.parse("2026-11-27T19:00:00.123Z"), message.creeLe)
        assertEquals(listOf(message), visibles())
        assertEquals(listOf(message), depot.aEnvoyer())
    }

    @Test
    fun `je ne suis pas mon propre destinataire`() = runTest {
        val message = (depot.ecrire(couple, listOf(mymy, yassine, mymy), "Test") as ResultatMessagerie.Fait).message
        assertEquals(listOf(mymy), message.accuses.map { it.destinataire })
        assertEquals(
            ResultatMessagerie.Refuse(ErreurMessagerie.AucunDestinataire),
            depot.ecrire(couple, listOf(yassine), "Test"),
        )
    }

    @Test
    fun `un texte vide ou trop long n'est pas enregistre`() = runTest {
        assertEquals(ResultatMessagerie.Refuse(ErreurMessagerie.TexteVide), depot.ecrire(couple, listOf(mymy), "  \n "))
        assertEquals(
            ResultatMessagerie.Refuse(ErreurMessagerie.TexteTropLong(10_000)),
            depot.ecrire(couple, listOf(mymy), "a".repeat(10_001)),
        )
        assertTrue(visibles().isEmpty())
    }

    @Test
    fun `cycle de vie complet d'un message envoye`() = runTest {
        val id = ecrire().id
        assertEquals(StatutMessage.Envoye, ReglesMessages.statut(depot.marquerEnvoye(id).message()))
        assertTrue(depot.aEnvoyer().isEmpty())
        assertEquals(StatutMessage.Recu, ReglesMessages.statut(depot.appliquerAccuse(id, mymy, TypeAccuse.Recu, t0).message()))
        assertEquals(StatutMessage.Lu, ReglesMessages.statut(depot.appliquerAccuse(id, mymy, TypeAccuse.Lu, t0).message()))
        // Un « reçu » rejoué après le « lu » ne fait pas reculer.
        assertEquals(StatutMessage.Lu, ReglesMessages.statut(depot.appliquerAccuse(id, mymy, TypeAccuse.Recu, t0).message()))
    }

    @Test
    fun `echec puis nouvel essai`() = runTest {
        val id = ecrire().id
        assertEquals(EtatEnvoi.Echec, depot.marquerEchec(id).message().etatEnvoi)
        assertTrue(depot.aEnvoyer().isEmpty())
        assertEquals(EtatEnvoi.EnAttente, depot.reessayer(id).message().etatEnvoi)
        assertEquals(listOf(id), depot.aEnvoyer().map { it.id })
    }

    @Test
    fun `un echec signale apres l'envoi est refuse`() = runTest {
        val id = ecrire().id
        depot.marquerEnvoye(id)
        assertEquals(ResultatMessagerie.Refuse(ErreurMessagerie.EtatInattendu), depot.marquerEchec(id))
        assertEquals(ResultatMessagerie.Refuse(ErreurMessagerie.EtatInattendu), depot.reessayer(id))
        assertEquals(EtatEnvoi.Envoye, source.message(id)?.etatEnvoi)
    }

    @Test
    fun `un accuse prouve l'envoi meme si la reponse du serveur s'est perdue`() = runTest {
        val id = ecrire().id
        val apres = depot.appliquerAccuse(id, mymy, TypeAccuse.Recu, t0).message()
        assertEquals(EtatEnvoi.Envoye, apres.etatEnvoi)
        assertEquals(StatutMessage.Recu, ReglesMessages.statut(apres))
        assertTrue(depot.aEnvoyer().isEmpty())
    }

    @Test
    fun `un accuse d'un inconnu ou sur un message recu est refuse`() = runTest {
        val sortant = ecrire().id
        assertEquals(
            ResultatMessagerie.Refuse(ErreurMessagerie.AccuseInattendu),
            depot.appliquerAccuse(sortant, IdMembre("inconnu"), TypeAccuse.Lu, t0),
        )
        val recu = depot.recevoir(entrant()).message().id
        assertEquals(
            ResultatMessagerie.Refuse(ErreurMessagerie.AccuseInattendu),
            depot.appliquerAccuse(recu, mymy, TypeAccuse.Lu, t0),
        )
        assertEquals(
            ResultatMessagerie.Refuse(ErreurMessagerie.MessageInconnu),
            depot.appliquerAccuse(IdMessage("absent"), mymy, TypeAccuse.Lu, t0),
        )
    }

    @Test
    fun `une operation d'envoi sur un message recu est refusee`() = runTest {
        val recu = depot.recevoir(entrant()).message().id
        assertEquals(ResultatMessagerie.Refuse(ErreurMessagerie.EtatInattendu), depot.marquerEnvoye(recu))
    }

    @Test
    fun `un message recu deux fois n'est enregistre qu'une fois`() = runTest {
        val premier = depot.recevoir(entrant()).message()
        assertEquals(Instant.parse("2026-11-27T18:59:30.123Z"), premier.creeLe)
        assertEquals(Instant.parse("2026-11-27T19:00:00.123Z"), premier.arriveLe)
        assertEquals(ResultatMessagerie.Fait(premier), depot.recevoir(entrant()))
        assertEquals(1, visibles().size)
    }

    @Test
    fun `un identifiant repris par un autre auteur est refuse`() = runTest {
        depot.recevoir(entrant())
        val usurpe = entrant().copy(auteur = IdMembre("autre"), texte = "Faux")
        assertEquals(ResultatMessagerie.Refuse(ErreurMessagerie.Incoherent), depot.recevoir(usurpe))
        assertEquals(ContenuMessage.Texte("Vers 19 h"), visibles().single().contenu)
    }

    @Test
    fun `un message supprime pour moi ne revient pas avec un renvoi`() = runTest {
        val id = depot.recevoir(entrant()).message().id
        depot.supprimerPourMoi(id)
        assertTrue(visibles().isEmpty())
        depot.recevoir(entrant())
        assertTrue(visibles().isEmpty())
        assertNull(source.message(id)?.contenu)
    }

    @Test
    fun `repondre en citant un message`() = runTest {
        val cite = depot.recevoir(entrant()).message().id
        val reponse = ecrire("Parfait", reponseA = cite)
        assertEquals(cite, reponse.reponseA)
        assertEquals(
            ResultatMessagerie.Refuse(ErreurMessagerie.CitationImpossible),
            depot.ecrire(couple, listOf(mymy), "Hein ?", IdMessage("absent")),
        )
        // Un message reçu peut citer un message que je n'ai plus : il est gardé tel quel.
        val orphelin = depot.recevoir(entrant(id = "entrant-2", reponseA = IdMessage("efface"))).message()
        assertEquals(IdMessage("efface"), orphelin.reponseA)
    }

    @Test
    fun `on ne cite pas un message supprime`() = runTest {
        val id = ecrire().id
        depot.supprimerPourTous(id)
        assertEquals(
            ResultatMessagerie.Refuse(ErreurMessagerie.CitationImpossible),
            depot.ecrire(couple, listOf(mymy), "Et ça ?", id),
        )
    }

    @Test
    fun `reactions des deux membres`() = runTest {
        val id = depot.recevoir(entrant()).message().id
        depot.reagir(id, "❤️")
        val apres = depot.recevoirReaction(id, mymy, "😂").message()
        assertEquals(setOf(Reaction(yassine, "❤️"), Reaction(mymy, "😂")), apres.reactions.toSet())
        assertEquals(listOf(Reaction(mymy, "😂")), depot.reagir(id, null).message().reactions)
        assertEquals(ResultatMessagerie.Refuse(ErreurMessagerie.ReactionInvalide), depot.reagir(id, "bravo"))
    }

    @Test
    fun `reagir a un message supprime`() = runTest {
        val id = ecrire().id
        depot.supprimerPourTous(id)
        assertEquals(ResultatMessagerie.Refuse(ErreurMessagerie.DejaSupprime), depot.reagir(id, "👍"))
        // Venue du réseau, elle est ignorée sans erreur.
        assertTrue(depot.recevoirReaction(id, mymy, "👍").message().reactions.isEmpty())
    }

    @Test
    fun `supprimer pour les deux garde la mention et efface le reste`() = runTest {
        val cite = depot.recevoir(entrant()).message().id
        val id = ecrire("Oups", reponseA = cite).id
        depot.reagir(id, "😅")
        val supprime = depot.supprimerPourTous(id).message()
        assertTrue(supprime.supprimePourTous)
        assertNull(supprime.contenu)
        assertNull(supprime.reponseA)
        assertTrue(supprime.reactions.isEmpty())
        assertEquals(2, visibles().size)
        assertEquals(ResultatMessagerie.Refuse(ErreurMessagerie.DejaSupprime), depot.supprimerPourTous(id))
    }

    @Test
    fun `je ne supprime pas pour les deux le message de Mymy`() = runTest {
        val id = depot.recevoir(entrant()).message().id
        assertEquals(ResultatMessagerie.Refuse(ErreurMessagerie.PasAuteur), depot.supprimerPourTous(id))
    }

    @Test
    fun `suppression demandee par l'auteur, idempotente`() = runTest {
        val id = depot.recevoir(entrant()).message().id
        assertEquals(ResultatMessagerie.Refuse(ErreurMessagerie.PasAuteur), depot.recevoirSuppression(id, yassine))
        assertTrue(depot.recevoirSuppression(id, mymy).message().supprimePourTous)
        assertTrue(depot.recevoirSuppression(id, mymy).message().supprimePourTous)
        assertNull(visibles().single().contenu)
    }

    @Test
    fun `une panne du stockage devient un echec sans exception`() = runTest {
        source.panne = IOException("disque plein")
        val resultat = depot.ecrire(couple, listOf(mymy), "Test")
        assertTrue(resultat is ResultatMessagerie.Echec)
        assertTrue(visibles().isEmpty())
    }

    @Test
    fun `seuls les messages de la conversation sont visibles`() = runTest {
        ecrire()
        depot.ecrire(IdConversation("autre"), listOf(mymy), "Ailleurs")
        assertEquals(1, visibles().size)
    }
}
