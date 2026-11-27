package fr.cocooning.core.securite

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Même format que [ChiffreurKeystore], avec une clé en mémoire puisque le Keystore n'existe pas sur la JVM. */
internal class ChiffreurEnMemoire(private val cle: ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)) : Chiffreur {
    override fun chiffrer(clair: ByteArray, contexte: ByteArray?): ByteArray {
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(cle, "AES"), GCMParameterSpec(128, iv))
        contexte?.let(c::updateAAD)
        return Enveloppe(iv, c.doFinal(clair)).versOctets()
    }

    override fun dechiffrer(enveloppe: ByteArray, contexte: ByteArray?): ByteArray {
        val e = Enveloppe.depuisOctets(enveloppe)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(cle, "AES"), GCMParameterSpec(128, e.iv))
        contexte?.let(c::updateAAD)
        return c.doFinal(e.contenu)
    }
}

class PreferencesChiffreesTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private val prenom = stringPreferencesKey("prenom")
    private val partage = booleanPreferencesKey("partage_cycle")
    private val delai = intPreferencesKey("verrouillage_minutes")

    private suspend fun SerialiseurChiffre<androidx.datastore.preferences.core.Preferences>.ecrire(
        p: androidx.datastore.preferences.core.Preferences,
    ) = ByteArrayOutputStream().also { writeTo(p, it) }.toByteArray()

    @Test
    fun lesPreferencesFontLAllerRetourEtNApparaissentPasEnClair() = runTest {
        val serialiseur = SerialiseurChiffre(PreferencesFileSerializer, ChiffreurEnMemoire(), "reglages")
        val avant = mutablePreferencesOf(prenom to "Mymy", partage to true, delai to 5)

        val octets = serialiseur.ecrire(avant)

        assertFalse(String(octets, Charsets.ISO_8859_1).contains("Mymy"))
        assertFalse(String(octets, Charsets.ISO_8859_1).contains("partage_cycle"))
        assertEquals(avant, serialiseur.readFrom(ByteArrayInputStream(octets)))
    }

    @Test(expected = CorruptionException::class)
    fun unFichierModifieEstRefuse() = runTest {
        val serialiseur = SerialiseurChiffre(PreferencesFileSerializer, ChiffreurEnMemoire(), "reglages")
        val octets = serialiseur.ecrire(mutablePreferencesOf(prenom to "Mymy"))
        octets[octets.size - 1] = (octets[octets.size - 1].toInt() xor 1).toByte()
        serialiseur.readFrom(ByteArrayInputStream(octets))
    }

    @Test(expected = CorruptionException::class)
    fun unFichierCopieDUnAutreMagasinEstRefuse() = runTest {
        val chiffreur = ChiffreurEnMemoire()
        val octets = SerialiseurChiffre(PreferencesFileSerializer, chiffreur, "reglages").ecrire(mutablePreferencesOf(prenom to "Mymy"))
        SerialiseurChiffre(PreferencesFileSerializer, chiffreur, "autre").readFrom(ByteArrayInputStream(octets))
    }

    @Test(expected = CorruptionException::class)
    fun sansLaBonneCleLeFichierEstIllisible() = runTest {
        val octets = SerialiseurChiffre(PreferencesFileSerializer, ChiffreurEnMemoire(), "reglages").ecrire(mutablePreferencesOf(prenom to "Mymy"))
        SerialiseurChiffre(PreferencesFileSerializer, ChiffreurEnMemoire(), "reglages").readFrom(ByteArrayInputStream(octets))
    }

    @Test
    fun unDataStoreChiffreRelitSesValeursEtRepartDeZeroSiLaCleEstPerdue() = runTest {
        val fichier = dossier.newFile("reglages.preferences_pb.chiffre").also { it.delete() }
        val premier = DataStoreFactory.create(
            serializer = SerialiseurChiffre(PreferencesFileSerializer, ChiffreurEnMemoire(), "reglages"),
            scope = backgroundScope,
            produceFile = { fichier },
        )
        premier.edit { it[prenom] = "Mymy"; it[delai] = 5 }
        assertEquals("Mymy", premier.data.first()[prenom])
        assertTrue(fichier.length() > 0)

        // Autre clé (Keystore effacé) : le gestionnaire de corruption rend des préférences vides.
        val dossierBis = dossier.newFolder("bis")
        val copie = java.io.File(dossierBis, fichier.name).also { fichier.copyTo(it) }
        val second = DataStoreFactory.create(
            serializer = SerialiseurChiffre(PreferencesFileSerializer, ChiffreurEnMemoire(), "reglages"),
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = backgroundScope,
            produceFile = { copie },
        )
        assertEquals(emptyPreferences(), second.data.first())
    }
}
