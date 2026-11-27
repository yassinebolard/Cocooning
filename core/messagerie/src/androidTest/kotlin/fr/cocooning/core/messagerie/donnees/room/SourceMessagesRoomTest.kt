package fr.cocooning.core.messagerie.donnees.room

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.cocooning.core.messagerie.domaine.ContenuMessage
import fr.cocooning.core.messagerie.domaine.EtatEnvoi
import fr.cocooning.core.messagerie.domaine.IdConversation
import fr.cocooning.core.messagerie.domaine.IdMembre
import fr.cocooning.core.messagerie.domaine.IdMessage
import fr.cocooning.core.messagerie.domaine.MessageEntrant
import fr.cocooning.core.messagerie.domaine.Reaction
import fr.cocooning.core.messagerie.domaine.ReglesMessages
import fr.cocooning.core.messagerie.domaine.StatutMessage
import fr.cocooning.core.messagerie.domaine.TypeAccuse
import fr.cocooning.core.messagerie.donnees.MessagerieRepositoryLocal
import fr.cocooning.core.messagerie.donnees.ResultatMessagerie
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Le dépôt sur la vraie base Room (en mémoire ici ; SQLCipher est testé par :core:securite). */
@RunWith(AndroidJUnit4::class)
class SourceMessagesRoomTest {

    private val yassine = IdMembre("yassine")
    private val mymy = IdMembre("mymy")
    private val couple = IdConversation("couple")
    private val t0: Instant = Instant.parse("2026-11-27T19:00:00.123Z")

    private val base = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        BaseMessagerie::class.java,
    ).build()
    private val source = SourceMessagesRoom(base)
    private var compteur = 0
    private val depot = MessagerieRepositoryLocal(
        source = source,
        moi = yassine,
        horloge = Clock.fixed(t0, ZoneOffset.UTC),
        nouvelId = { IdMessage("sortant-${++compteur}") },
    )

    @After
    fun fermer() = base.close()

    private fun ResultatMessagerie.id() = (this as ResultatMessagerie.Fait).message.id

    @Test
    fun allerRetourComplet() = runTest {
        val recu = depot.recevoir(MessageEntrant(IdMessage("e1"), couple, mymy, "Vers 19 h", null, t0.minusSeconds(30))).id()
        val sortant = depot.ecrire(couple, listOf(mymy), "Parfait", reponseA = recu).id()
        assertEquals(listOf(sortant), depot.aEnvoyer().map { it.id })

        depot.marquerEnvoye(sortant)
        depot.appliquerAccuse(sortant, mymy, TypeAccuse.Lu, t0.plusSeconds(5))
        depot.reagir(recu, "❤️")
        depot.recevoirReaction(sortant, mymy, "😂")

        val conversation = depot.conversation(couple).first()
        assertEquals(listOf(recu, sortant), conversation.map { it.id })
        val relu = conversation.last()
        assertEquals(ContenuMessage.Texte("Parfait"), relu.contenu)
        assertEquals(recu, relu.reponseA)
        assertEquals(StatutMessage.Lu, ReglesMessages.statut(relu))
        assertEquals(t0.plusSeconds(5), relu.accuses.single().luLe)
        assertEquals(listOf(Reaction(mymy, "😂")), relu.reactions)
        assertEquals(listOf(Reaction(yassine, "❤️")), conversation.first().reactions)
        assertTrue(depot.aEnvoyer().isEmpty())
    }

    @Test
    fun suppressions() = runTest {
        val recu = depot.recevoir(MessageEntrant(IdMessage("e1"), couple, mymy, "Secret", null, t0)).id()
        val sortant = depot.ecrire(couple, listOf(mymy), "Oups").id()
        depot.reagir(sortant, "😅")

        depot.supprimerPourMoi(recu)
        depot.supprimerPourTous(sortant)

        val conversation = depot.conversation(couple).first()
        assertEquals(listOf(sortant), conversation.map { it.id })
        assertTrue(conversation.single().supprimePourTous)
        assertNull(conversation.single().contenu)
        assertTrue(conversation.single().reactions.isEmpty())
        // La trace du message masqué reste, sans contenu, pour qu'un renvoi ne le fasse pas revenir.
        assertNull(source.message(recu)?.contenu)
        depot.recevoir(MessageEntrant(IdMessage("e1"), couple, mymy, "Secret", null, t0))
        assertEquals(1, depot.conversation(couple).first().size)
        assertEquals(EtatEnvoi.EnAttente, source.message(sortant)?.etatEnvoi)
    }
}
