package fr.cocooning.core.securite

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Préférences chiffrées par le vrai Keystore, et migration depuis d'anciennes SharedPreferences. */
@RunWith(AndroidJUnit4::class)
class PreferencesChiffreesAppareilTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nom = "essai_preferences"
    private val ancien = "essai_anciennes_preferences"

    @Before
    fun nettoyer() {
        PreferencesChiffrees.fichier(context, nom).delete()
        context.deleteSharedPreferences(ancien)
    }

    @Test
    fun lesAnciennesPreferencesSontMigreesSansPerteEtLeFichierEnClairDisparait() = runBlocking {
        context.getSharedPreferences(ancien, Context.MODE_PRIVATE).edit()
            .putString("prenom", "Mymy").putBoolean("partage_cycle", true).putInt("verrouillage_minutes", 5)
            .commit()

        val portee = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val magasin = PreferencesChiffrees.ouvrir(context, nom, listOf(ancien), portee)
        val lues = magasin.data.first()

        assertEquals("Mymy", lues[stringPreferencesKey("prenom")])
        assertEquals(true, lues[booleanPreferencesKey("partage_cycle")])
        assertEquals(5, lues[intPreferencesKey("verrouillage_minutes")])

        val fichierEnClair = File(context.dataDir, "shared_prefs/$ancien.xml")
        assertFalse("L'ancien fichier en clair doit disparaître", fichierEnClair.exists())

        magasin.edit { it[stringPreferencesKey("prenom")] = "Yassine" }
        val chiffre = PreferencesChiffrees.fichier(context, nom)
        assertTrue(chiffre.exists())
        val octets = String(chiffre.readBytes(), Charsets.ISO_8859_1)
        assertFalse(octets.contains("Yassine"))
        assertFalse(octets.contains("prenom"))
        portee.cancel()
    }
}
