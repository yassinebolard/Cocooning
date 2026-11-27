package fr.cocooning.prototype.sos

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Réglages du prototype, gardés dans des préférences simples (pas de base : code jetable). */
class Reglages(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("prototype_sos", Context.MODE_PRIVATE)

    /** Sujet ntfy partagé par les deux téléphones : c'est le « code d'appairage » du prototype. */
    var sujet: String
        get() = prefs.getString("sujet", null) ?: genererSujet().also { sujet = it }
        set(valeur) = prefs.edit().putString("sujet", valeur.trim()).apply()

    var serveurNtfy: String
        get() = prefs.getString("serveur", "https://ntfy.sh")!!
        set(valeur) = prefs.edit().putString("serveur", valeur.trim().trimEnd('/')).apply()

    var nom: String
        get() = prefs.getString("nom", Build.MODEL)!!
        set(valeur) = prefs.edit().putString("nom", valeur.trim()).apply()

    var numeroSms: String
        get() = prefs.getString("numeroSms", "")!!
        set(valeur) = prefs.edit().putString("numeroSms", valeur.trim()).apply()

    /** Faux par défaut : sur le serveur public ntfy.sh, la position ne part pas. */
    var envoyerPosition: Boolean
        get() = prefs.getBoolean("envoyerPosition", false)
        set(valeur) = prefs.edit().putBoolean("envoyerPosition", valeur).apply()

    var ecouteActive: Boolean
        get() = prefs.getBoolean("ecoute", false)
        set(valeur) = prefs.edit().putBoolean("ecoute", valeur).apply()

    var dernierIdNtfy: String?
        get() = prefs.getString("dernierIdNtfy", null)
        set(valeur) = prefs.edit().putString("dernierIdNtfy", valeur).apply()

    var jetonFcm: String?
        get() = prefs.getString("jetonFcm", null)
        set(valeur) = prefs.edit().putString("jetonFcm", valeur).apply()

    /** Version du canal S.O.S : on le recrée après l'accès Ne pas déranger (voir Canaux). */
    var versionCanal: Int
        get() = prefs.getInt("versionCanal", 1)
        set(valeur) = prefs.edit().putInt("versionCanal", valeur).apply()

    /** Alerte en cours côté récepteur, au format JSON du message reçu. */
    var alerteCourante: String?
        get() = prefs.getString("alerteCourante", null)
        set(valeur) = prefs.edit().putString("alerteCourante", valeur).apply()

    /** Volume et mode sonnerie d'avant l'alerte, pour les rétablir même après un plantage. */
    var volumeAvant: Int
        get() = prefs.getInt("volumeAvant", -1)
        set(valeur) = prefs.edit().putInt("volumeAvant", valeur).apply()
    var modeSonnerieAvant: Int
        get() = prefs.getInt("modeSonnerieAvant", -1)
        set(valeur) = prefs.edit().putInt("modeSonnerieAvant", valeur).apply()

    var filtreAvant: Int
        get() = prefs.getInt("filtreAvant", -1)
        set(valeur) = prefs.edit().putInt("filtreAvant", valeur).apply()

    /** Évite de sonner deux fois pour la même alerte reçue par deux voies (FCM et ntfy). */
    @Synchronized
    fun dejaTraitee(idAlerte: String): Boolean {
        val vues = prefs.getString("alertesVues", "")!!.split(',').filter { it.isNotBlank() }
        if (idAlerte in vues) return true
        prefs.edit().putString("alertesVues", (vues.takeLast(49) + idAlerte).joinToString(",")).apply()
        return false
    }

    // Journal : texte brut, le plus récent en haut, aussi écrit dans logcat (étiquette SOSPROTO).
    fun journaliser(ligne: String) {
        val horodatee = "${HEURE.format(Date())}  $ligne"
        Log.i("SOSPROTO", horodatee)
        synchronized(Reglages::class.java) {
            val actuel = prefs.getString("journal", "")!!
            prefs.edit().putString("journal", (horodatee + "\n" + actuel).take(60_000)).apply()
        }
    }

    val journal: String get() = prefs.getString("journal", "")!!
    fun viderJournal() = prefs.edit().remove("journal").apply()

    companion object {
        private val HEURE = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE)

        private fun genererSujet(): String {
            val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
            val hasard = SecureRandom()
            return "cocooning-proto-" + (1..16).map { alphabet[hasard.nextInt(alphabet.length)] }.joinToString("")
        }
    }
}
