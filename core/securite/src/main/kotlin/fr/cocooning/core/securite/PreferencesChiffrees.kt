package fr.cocooning.core.securite

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Préférences chiffrées (seconde moitié de ENF-SEC-04).
 *
 * Un DataStore Preferences ordinaire pour l'appelant, mais le fichier sur le disque est une
 * enveloppe AES-256-GCM dont la clé vit dans l'Android Keystore (alias distinct de celui des
 * bases). Le fichier est rangé dans `no_backup/preferences/` : comme les phrases de passe des
 * bases, il n'est jamais sauvegardé par Android, puisque la clé ne serait pas là pour l'ouvrir.
 *
 * Migration sans perte : les anciens fichiers SharedPreferences nommés dans [ouvrir] sont recopiés
 * dans le magasin chiffré à la première lecture, puis supprimés par DataStore une fois la copie
 * écrite. Si l'écriture échoue, l'ancien fichier reste en place et la migration est retentée.
 *
 * Exception assumée : `osmdroid.xml` reste en clair, c'est la bibliothèque de carte qui le lit et
 * il ne contient que des réglages techniques (taille du cache, nombre de fils), rien de personnel.
 *
 * Une seule instance par [nom] et par processus (règle de DataStore) : la garder dans le conteneur.
 */
object PreferencesChiffrees {

    private const val JOURNAL = "PreferencesChiffrees"
    internal const val ALIAS = "cocooning_preferences"

    fun ouvrir(
        context: Context,
        nom: String,
        anciennesPreferences: List<String> = emptyList(),
        portee: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    ): DataStore<Preferences> {
        val appli = context.applicationContext
        val migrations: List<DataMigration<Preferences>> =
            anciennesPreferences.map { ancien -> SharedPreferencesMigration(appli, ancien) }
        return DataStoreFactory.create(
            serializer = SerialiseurChiffre(PreferencesFileSerializer, ChiffreurKeystore(ALIAS), nom),
            corruptionHandler = ReplaceFileCorruptionHandler { erreur ->
                // Clé du Keystore perdue ou fichier abîmé : illisible pour tout le monde.
                // On repart de zéro plutôt que de bloquer l'application.
                Log.w(JOURNAL, "Préférences $nom illisibles, remises à zéro.", erreur)
                emptyPreferences()
            },
            migrations = migrations,
            scope = portee,
            produceFile = { fichier(appli, nom) },
        )
    }

    internal fun fichier(context: Context, nom: String) =
        File(File(context.noBackupFilesDir, "preferences"), "$nom.preferences_pb.chiffre")
}

/**
 * Enveloppe un [Serializer] DataStore : les octets produits par [base] sont chiffrés par
 * [chiffreur] avant d'être écrits, et déchiffrés avant d'être relus. Le [nom] du magasin est
 * authentifié avec le contenu : un fichier copié d'un magasin vers un autre est refusé.
 */
internal class SerialiseurChiffre<T>(
    private val base: Serializer<T>,
    private val chiffreur: Chiffreur,
    nom: String,
) : Serializer<T> {

    private val contexte = "cocooning-preferences:$nom".toByteArray(Charsets.UTF_8)

    override val defaultValue: T get() = base.defaultValue

    override suspend fun readFrom(input: InputStream): T {
        val chiffre = input.readBytes()
        if (chiffre.isEmpty()) return base.defaultValue
        val clair = try {
            chiffreur.dechiffrer(chiffre, contexte)
        } catch (e: Exception) {
            throw CorruptionException("Préférences chiffrées illisibles", e)
        }
        return base.readFrom(ByteArrayInputStream(clair))
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        val clair = ByteArrayOutputStream().also { base.writeTo(t, it) }.toByteArray()
        output.write(chiffreur.chiffrer(clair, contexte))
    }
}
