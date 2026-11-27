package fr.cocooning.core.chiffrement.room

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.cocooning.core.chiffrement.AdresseAppareil
import fr.cocooning.core.chiffrement.ChangementIdentite
import fr.cocooning.core.chiffrement.ExceptionChiffrement
import fr.cocooning.core.chiffrement.MoteurSignal
import fr.cocooning.core.chiffrement.PaquetDestinataire
import fr.cocooning.core.chiffrement.TypeEnveloppe
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * libsignal pour Android (bibliothèque native du téléphone) sur la vraie base Room. Deux bases
 * en mémoire jouent les deux téléphones ; SQLCipher est testé par :core:securite.
 */
@RunWith(AndroidJUnit4::class)
class StockageSignalRoomTest {

    private val contexte = InstrumentationRegistry.getInstrumentation().targetContext
    private val baseYassine = Room.inMemoryDatabaseBuilder(contexte, BaseSignal::class.java).build()
    private val baseMymy = Room.inMemoryDatabaseBuilder(contexte, BaseSignal::class.java).build()

    @After
    fun fermer() {
        baseYassine.close()
        baseMymy.close()
    }

    @Test
    fun conversationChiffreeEntreDeuxTelephones() {
        val adresseYassine = AdresseAppareil("yassine")
        val adresseMymy = AdresseAppareil("mymy")
        val yassine = MoteurSignal(StockageSignalRoom(baseYassine))
        val mymy = MoteurSignal(StockageSignalRoom(baseMymy))
        yassine.initialiser()
        mymy.initialiser()

        val publication = mymy.clesAPublier()!!
        assertEquals(100, publication.preClesUniques.size)
        val paquet = PaquetDestinataire(
            publication.idEnregistrement, publication.appareil, publication.identite,
            publication.preCleSignee, publication.preCleKyber, publication.preClesUniques.first(),
        )
        assertEquals(ChangementIdentite.Nouvelle, yassine.ouvrirSession(adresseMymy, paquet))

        val ouverture = yassine.chiffrer(adresseMymy, "Bonsoir mon cœur".toByteArray())
        assertEquals(TypeEnveloppe.OuvertureSession, ouverture.type)
        assertEquals("Bonsoir mon cœur", String(mymy.dechiffrer(adresseYassine, ouverture).clair))
        val reponse = mymy.chiffrer(adresseYassine, "Coucou".toByteArray())
        assertEquals("Coucou", String(yassine.dechiffrer(adresseMymy, reponse).clair))

        // La pré-clé unique utilisée a été effacée ; un rejeu de l'ouverture est refusé.
        assertThrows(ExceptionChiffrement::class.java) { mymy.dechiffrer(adresseYassine, ouverture) }

        // Moteur recréé sur la même base (redémarrage de l'appli) : identité et session gardées.
        val relance = MoteurSignal(StockageSignalRoom(baseMymy))
        assertFalse(relance.initialiser())
        assertArrayEquals(mymy.identitePublique(), relance.identitePublique())
        val suite = yassine.chiffrer(adresseMymy, "Après redémarrage".toByteArray())
        assertEquals(TypeEnveloppe.Message, suite.type)
        assertEquals("Après redémarrage", String(relance.dechiffrer(adresseYassine, suite).clair))

        assertEquals(
            yassine.codeSecurite("yassine", "mymy", mymy.identitePublique()).chiffres,
            mymy.codeSecurite("mymy", "yassine", yassine.identitePublique()).chiffres,
        )
    }
}
