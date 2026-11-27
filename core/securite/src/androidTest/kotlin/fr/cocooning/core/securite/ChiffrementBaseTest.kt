package fr.cocooning.core.securite

import android.database.sqlite.SQLiteDatabase as BaseAndroid
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.SecureRandom

/** Chiffrement sur place d'une base en clair laissée par une version précédente, sur un vrai téléphone. */
@RunWith(AndroidJUnit4::class)
class ChiffrementBaseTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var base: File

    @Before
    fun preparer() {
        System.loadLibrary("sqlcipher")
        base = context.getDatabasePath("essai_chiffrement.db")
        base.parentFile?.mkdirs()
        FichierBase.supprimer(base)
    }

    @Test
    fun uneBaseEnClairEnWalEstChiffreeSansPerte() {
        // Base en clair en mode WAL, comme celle de Room, avec des données encore dans le WAL.
        val claire = BaseAndroid.openOrCreateDatabase(base, null)
        claire.enableWriteAheadLogging()
        claire.execSQL("CREATE TABLE cycles (id INTEGER PRIMARY KEY, debut INTEGER NOT NULL)")
        claire.execSQL("INSERT INTO cycles (debut) VALUES (20000), (20028)")
        claire.version = 1
        claire.close()
        assertEquals(EtatFichier.EnClair, FichierBase.etat(base))

        val phrase = PhraseDePasse.nouvelle(SecureRandom())
        BaseChiffree.chiffrerSurPlace(base, phrase)

        assertEquals(EtatFichier.Chiffre, FichierBase.etat(base))
        val chiffree = SQLiteDatabase.openDatabase(base.path, phrase, null, SQLiteDatabase.OPEN_READWRITE, null, null)
        try {
            assertEquals(1, chiffree.version)
            chiffree.rawQuery("SELECT count(*), sum(debut) FROM cycles", null).use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
                assertEquals(40028L, c.getLong(1))
            }
        } finally {
            chiffree.close()
        }
    }
}
