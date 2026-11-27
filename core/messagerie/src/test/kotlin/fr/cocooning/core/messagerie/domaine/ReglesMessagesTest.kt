package fr.cocooning.core.messagerie.domaine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReglesMessagesTest {

    private val yassine = IdMembre("yassine")
    private val mymy = IdMembre("mymy")
    private val couple = IdConversation("couple")
    private val t0: Instant = Instant.parse("2026-11-27T19:00:00Z")

    private fun message(
        id: String = "m1",
        auteur: IdMembre = yassine,
        creeLe: Instant = t0,
        arriveLe: Instant = creeLe,
        etat: EtatEnvoi = EtatEnvoi.Envoye,
        accuses: List<Accuse> = emptyList(),
    ) = Message(
        id = IdMessage(id),
        conversation = couple,
        auteur = auteur,
        sortant = auteur == yassine,
        contenu = ContenuMessage.Texte("Bonsoir"),
        reponseA = null,
        creeLe = creeLe,
        arriveLe = arriveLe,
        etatEnvoi = etat,
        accuses = accuses,
    )

    @Test
    fun `le texte est nettoye au debut et a la fin seulement`() {
        assertEquals("Ligne 1\n\n  Ligne 2", ReglesMessages.normaliser("\r\n  Ligne 1\r\n\r\n  Ligne 2  \n"))
    }

    @Test
    fun `un texte vide ou fait d'espaces est refuse`() {
        assertEquals(ErreurMessagerie.TexteVide, ReglesMessages.validerTexte(ReglesMessages.normaliser(" \n\t ")))
    }

    @Test
    fun `la longueur se compte en caracteres et non en unites UTF-16`() {
        val coeurs = "❤️".repeat(5) // 2 caractères chacun : cœur et sélecteur de variante
        val emojis = "😀".repeat(10) // 1 caractère chacun, 2 unités UTF-16
        assertNull(ReglesMessages.validerTexte(emojis, longueurMax = 10))
        assertEquals(ErreurMessagerie.TexteTropLong(9), ReglesMessages.validerTexte(coeurs, longueurMax = 9))
        assertNull(ReglesMessages.validerTexte("a".repeat(10_000)))
        assertEquals(ErreurMessagerie.TexteTropLong(10_000), ReglesMessages.validerTexte("a".repeat(10_001)))
    }

    @Test
    fun `les reactions acceptent les emojis composes et refusent le texte`() {
        listOf("❤️", "👍🏽", "👨‍👩‍👧", "🇫🇷", "1️⃣").forEach { assertNull(it, ReglesMessages.validerReaction(it)) }
        listOf("", "ok", "❤️ ", "x".repeat(40)).forEach {
            assertEquals(it, ErreurMessagerie.ReactionInvalide, ReglesMessages.validerReaction(it))
        }
    }

    @Test
    fun `le statut suit l'envoi puis le plus lent des destinataires`() {
        val leo = IdMembre("leo")
        assertEquals(StatutMessage.EnAttente, ReglesMessages.statut(message(etat = EtatEnvoi.EnAttente)))
        assertEquals(StatutMessage.Echec, ReglesMessages.statut(message(etat = EtatEnvoi.Echec)))
        assertEquals(StatutMessage.Envoye, ReglesMessages.statut(message(accuses = listOf(Accuse(mymy)))))
        assertEquals(StatutMessage.Recu, ReglesMessages.statut(message(accuses = listOf(Accuse(mymy, recuLe = t0)))))
        assertEquals(StatutMessage.Lu, ReglesMessages.statut(message(accuses = listOf(Accuse(mymy, t0, t0)))))
        // Famille (V2) : lu par l'un, pas encore reçu par l'autre.
        assertEquals(
            StatutMessage.Envoye,
            ReglesMessages.statut(message(accuses = listOf(Accuse(mymy, t0, t0), Accuse(leo)))),
        )
        assertEquals(
            StatutMessage.Recu,
            ReglesMessages.statut(message(accuses = listOf(Accuse(mymy, t0, t0), Accuse(leo, recuLe = t0)))),
        )
    }

    @Test
    fun `un accuse ne fait jamais reculer le statut`() {
        val lu = ReglesMessages.appliquerAccuse(Accuse(mymy), TypeAccuse.Lu, t0)
        assertEquals(Accuse(mymy, recuLe = t0, luLe = t0), lu)
        // Un « reçu » en retard, ou un « lu » rejoué, ne change rien.
        assertEquals(lu, ReglesMessages.appliquerAccuse(lu, TypeAccuse.Recu, t0.plusSeconds(5)))
        assertEquals(lu, ReglesMessages.appliquerAccuse(lu, TypeAccuse.Lu, t0.plusSeconds(9)))
        // Reçu d'abord, lu ensuite : les deux dates sont gardées.
        val recu = ReglesMessages.appliquerAccuse(Accuse(mymy), TypeAccuse.Recu, t0)
        assertEquals(Accuse(mymy, t0, t0.plusSeconds(60)), ReglesMessages.appliquerAccuse(recu, TypeAccuse.Lu, t0.plusSeconds(60)))
    }

    @Test
    fun `un message dont l'horloge avance ne s'affiche pas dans le futur`() {
        val enAvance = message(id = "b", auteur = mymy, creeLe = t0.plusSeconds(3600), arriveLe = t0.plusSeconds(10))
        val ensuite = message(id = "a", creeLe = t0.plusSeconds(20))
        assertEquals(t0.plusSeconds(10), ReglesMessages.heureAffichee(enAvance))
        assertEquals(listOf("b", "a"), ReglesMessages.visibles(listOf(ensuite, enAvance)).map { it.id.valeur })
    }

    @Test
    fun `l'ordre est stable a heure egale et les messages masques sont caches`() {
        val a = message(id = "a")
        val b = message(id = "b")
        val masque = message(id = "c").copy(masque = true)
        assertEquals(listOf("a", "b"), ReglesMessages.visibles(listOf(b, masque, a)).map { it.id.valeur })
    }

    @Test
    fun `on ne cite qu'un message visible de la meme conversation`() {
        assertTrue(ReglesMessages.peutCiter(message(), couple))
        assertFalse(ReglesMessages.peutCiter(null, couple))
        assertFalse(ReglesMessages.peutCiter(message(), IdConversation("autre")))
        assertFalse(ReglesMessages.peutCiter(message().copy(masque = true), couple))
        assertFalse(ReglesMessages.peutCiter(message().copy(supprimePourTous = true), couple))
    }

    @Test
    fun `seul l'auteur supprime pour les deux, une seule fois`() {
        assertNull(ReglesMessages.refusSuppressionPourTous(message(), yassine))
        assertEquals(ErreurMessagerie.PasAuteur, ReglesMessages.refusSuppressionPourTous(message(), mymy))
        assertEquals(
            ErreurMessagerie.DejaSupprime,
            ReglesMessages.refusSuppressionPourTous(message().copy(supprimePourTous = true), yassine),
        )
    }

    @Test
    fun `une reaction par membre, remplacee ou retiree`() {
        val une = ReglesMessages.reagir(emptyList(), mymy, "❤️")
        val deux = ReglesMessages.reagir(une, yassine, "😂")
        assertEquals(listOf(Reaction(mymy, "❤️"), Reaction(yassine, "😂")), deux)
        assertEquals(listOf(Reaction(yassine, "😂"), Reaction(mymy, "👍")), ReglesMessages.reagir(deux, mymy, "👍"))
        assertEquals(listOf(Reaction(yassine, "😂")), ReglesMessages.reagir(deux, mymy, null))
    }
}
