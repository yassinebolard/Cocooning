package fr.cocooning.feature.sauvegarde

import android.content.Context
import androidx.core.net.toUri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Fichiers choisis dans le sélecteur d'Android (Storage Access Framework), sans permission de stockage. */
class FichiersAndroid(context: Context) : FichiersSauvegarde {

    private val resolveur = context.applicationContext.contentResolver

    override suspend fun ecrire(adresse: String, octets: ByteArray) = withContext(Dispatchers.IO) {
        val flux = resolveur.openOutputStream(adresse.toUri(), "w") ?: throw IOException("Écriture refusée")
        flux.use { it.write(octets) }
    }

    override suspend fun lire(adresse: String): ByteArray = withContext(Dispatchers.IO) {
        val flux = resolveur.openInputStream(adresse.toUri()) ?: throw IOException("Lecture refusée")
        flux.use { lireBorne(it) }
    }

    override suspend fun nom(adresse: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            resolveur.query(adresse.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
    }

    private fun lireBorne(flux: InputStream): ByteArray {
        val sortie = ByteArrayOutputStream()
        val tampon = ByteArray(64 * 1024)
        while (true) {
            val lus = flux.read(tampon)
            if (lus < 0) break
            if (sortie.size() + lus > TAILLE_MAX) throw FichierTropGros()
            sortie.write(tampon, 0, lus)
        }
        return sortie.toByteArray()
    }

    companion object {
        /** Bien au-delà d'une vraie sauvegarde ; protège la mémoire si l'on choisit une vidéo par erreur. */
        const val TAILLE_MAX = 128 * 1024 * 1024
    }
}
