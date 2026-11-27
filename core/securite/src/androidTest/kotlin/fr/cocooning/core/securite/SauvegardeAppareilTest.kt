package fr.cocooning.core.securite

import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.cocooning.core.securite.sauvegarde.EchecOuverture
import fr.cocooning.core.securite.sauvegarde.SauvegardeCocooning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

/**
 * Changement de téléphone simulé sur un vrai appareil : sauvegarde, puis effacement de la base,
 * de sa phrase de passe et des clés du Keystore, puis restauration avec la phrase de récupération.
 * L'appli de test a son propre Keystore et ses propres fichiers : les données de Cocooning ne sont pas touchées.
 */
@RunWith(AndroidJUnit4::class)
class SauvegardeAppareilTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nom = "essai_sauvegarde.db"
    private val phrase = "Le chat de Mymy dort sur le radiateur bleu"

    @Before
    fun nettoyer() {
        FichierBase.supprimer(context.getDatabasePath(nom))
        File(context.noBackupFilesDir, "cles/$nom.cle").delete()
        PreferencesChiffrees.fichier(context, "essai_sauvegarde").delete()
    }

    private fun ouvrir(): SupportSQLiteOpenHelper = BaseChiffree.fabrique(context, nom).create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(nom)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE cycles (id INTEGER PRIMARY KEY, debut INTEGER NOT NULL)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build(),
    )

    @Test
    fun uneSauvegardeSeRestaureSurUnTelephoneVierge() = runBlocking {
        val prenom = stringPreferencesKey("prenom")
        var portee = CoroutineScope(Dispatchers.IO + SupervisorJob())
        var magasin = PreferencesChiffrees.ouvrir(context, "essai_sauvegarde", portee = portee)
        magasin.edit { it[prenom] = "Mymy" }

        val aide = ouvrir()
        aide.writableDatabase.execSQL("INSERT INTO cycles (debut) VALUES (20000), (20028), (20057)")

        val debut = System.nanoTime()
        val fichier = SauvegardeCocooning(context, mapOf(nom to { aide.writableDatabase }), mapOf("essai_sauvegarde" to magasin))
            .exporter(phrase)
        Log.i("SauvegardeAppareilTest", "Export : ${(System.nanoTime() - debut) / 1_000_000} ms, ${fichier.size} octets")
        aide.close()
        portee.cancel()

        // Nouveau téléphone : plus de base, plus de phrase de passe, plus de clés Keystore.
        FichierBase.supprimer(context.getDatabasePath(nom))
        File(context.noBackupFilesDir, "cles/$nom.cle").delete()
        PreferencesChiffrees.fichier(context, "essai_sauvegarde").delete()
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(CoffreCles.ALIAS)
            deleteEntry(PreferencesChiffrees.ALIAS)
        }

        portee = CoroutineScope(Dispatchers.IO + SupervisorJob())
        magasin = PreferencesChiffrees.ouvrir(context, "essai_sauvegarde", portee = portee)
        val restauration = SauvegardeCocooning(context, mapOf(nom to { error("base fermée") }), mapOf("essai_sauvegarde" to magasin))
        try {
            restauration.lire(fichier, "Le chat de Mymy dort sur le radiateur vert")
            fail("Une mauvaise phrase doit être refusée")
        } catch (attendu: EchecOuverture.PhraseIncorrecte) {
        }
        val t = System.nanoTime()
        restauration.restaurer(restauration.lire(fichier, "  le chat de mymy dort sur le radiateur BLEU"))
        Log.i("SauvegardeAppareilTest", "Restauration : ${(System.nanoTime() - t) / 1_000_000} ms")

        assertEquals("Mymy", magasin.data.first()[prenom])
        val relue = ouvrir()
        try {
            relue.readableDatabase.query("SELECT count(*), sum(debut) FROM cycles").use { c ->
                c.moveToFirst()
                assertEquals(3, c.getInt(0))
                assertEquals(60085L, c.getLong(1))
            }
            assertEquals(3, relue.readableDatabase.version)
        } finally {
            relue.close()
            portee.cancel()
        }
    }
}
