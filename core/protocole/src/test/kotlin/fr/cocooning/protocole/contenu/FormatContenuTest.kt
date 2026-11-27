package fr.cocooning.protocole.contenu

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class FormatContenuTest {

    private fun base() = ContenuClair(id = "m1", conversation = "couple", creeLe = 1_795_000_000_123)

    @Test
    fun `aller-retour de chaque type`() {
        val contenus = listOf(
            base().copy(texte = ContenuClair.Texte("Bonsoir ❤️\nÀ tout à l'heure"), reponseA = "m0"),
            base().copy(reaction = ContenuClair.Reaction("m0", "😂")),
            base().copy(reaction = ContenuClair.Reaction("m0", null)),
            base().copy(suppression = ContenuClair.Suppression("m0")),
            base().copy(accuse = ContenuClair.Accuse(ContenuClair.TypeAccuse.Lu, listOf("a", "b"))),
            base().copy(accuse = ContenuClair.Accuse(ContenuClair.TypeAccuse.Recu, listOf("a"))),
            base().copy(ecriture = ContenuClair.Ecriture(enCours = true)),
        )
        contenus.forEach { assertEquals(it, FormatContenu.decoder(FormatContenu.encoder(it)).clair) }
    }

    @Test
    fun `le type decode correspond au champ rempli`() {
        val texte = FormatContenu.decoder(FormatContenu.encoder(base().copy(texte = ContenuClair.Texte("Coucou"))))
        assertEquals("Coucou", (texte as Contenu.Texte).corps)
        val accuse = FormatContenu.decoder(
            FormatContenu.encoder(base().copy(accuse = ContenuClair.Accuse(ContenuClair.TypeAccuse.Lu, listOf("x")))),
        )
        assertEquals(listOf("x"), (accuse as Contenu.Accuse).cibles)
        assertEquals(ContenuClair.TypeAccuse.Lu, accuse.type)
    }

    @Test
    fun `zero ou deux types a la fois sont refuses a l'encodage`() {
        assertThrows(IllegalArgumentException::class.java) { FormatContenu.encoder(base()) }
        assertThrows(IllegalArgumentException::class.java) {
            FormatContenu.encoder(base().copy(texte = ContenuClair.Texte("a"), suppression = ContenuClair.Suppression("b")))
        }
    }

    @Test
    fun `deux types a la fois sont refuses au decodage`() {
        val double = ProtoBuf.encodeToByteArray(
            base().copy(texte = ContenuClair.Texte("a"), suppression = ContenuClair.Suppression("b")),
        )
        assertThrows(ContenuInvalide::class.java) { FormatContenu.decoder(double) }
    }

    @Test
    fun `des octets qui ne sont pas un contenu sont refuses`() {
        assertThrows(ContenuInvalide::class.java) { FormatContenu.decoder(byteArrayOf(0x7f, 0x01, 0x02)) }
        assertThrows(ContenuInvalide::class.java) { FormatContenu.decoder(ByteArray(0)) }
    }

    /** Ce qu'une version future de l'appli pourrait envoyer : un champ 15 inconnu aujourd'hui. */
    @Serializable
    data class ContenuFutur(
        @ProtoNumber(1) val version: Int,
        @ProtoNumber(2) val id: String,
        @ProtoNumber(3) val conversation: String,
        @ProtoNumber(4) val creeLe: Long,
        @ProtoNumber(15) val sondage: Sondage,
    ) {
        @Serializable
        data class Sondage(@ProtoNumber(1) val question: String)
    }

    @Test
    fun `un type venu d'une version future est reconnu comme inconnu, sans erreur`() {
        val futur = ProtoBuf.encodeToByteArray(ContenuFutur(2, "m9", "couple", 1, ContenuFutur.Sondage("Pizza ?")))
        val contenu = FormatContenu.decoder(futur)
        assertTrue(contenu is Contenu.Inconnu)
        assertEquals("m9", contenu.clair.id)
        assertEquals(2, contenu.clair.version)
    }

    @Test
    fun `un texte court reste compact`() {
        val octets = FormatContenu.encoder(base().copy(texte = ContenuClair.Texte("Oui")))
        assertTrue("${octets.size} octets", octets.size < 40)
    }
}
