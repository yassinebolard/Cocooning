package fr.cocooning.prototype.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.CancellationSignal
import android.telephony.SmsManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Côté émetteur : envoie l'alerte tout de suite avec la dernière position connue, puis la
 * position fraîche, attend les accusés 60 s, et propose le SMS de secours (EF-SOS-08).
 */
class EnvoiAlerte(private val context: Context) {
    var etat by mutableStateOf("Prêt")
        private set
    var position by mutableStateOf<Location?>(null)
        private set
    var detailPosition by mutableStateOf("")
        private set
    var recue by mutableStateOf(false)
        private set
    var vue by mutableStateOf(false)
        private set
    var proposerSms by mutableStateOf(false)
        private set
    var enCours by mutableStateOf(false)
        private set

    private val reglages = Reglages(context)
    private val travail = Executors.newCachedThreadPool()
    private var idCourant = ""

    fun envoyer() {
        if (enCours) return
        enCours = true; recue = false; vue = false; proposerSms = false; position = null; detailPosition = ""
        val id = UUID.randomUUID().toString().take(8)
        idCourant = id
        val debut = System.currentTimeMillis()
        travail.execute {
            val derniere = derniereConnue()
            if (derniere != null) {
                position = derniere
                detailPosition = "Dernière connue : ±${derniere.accuracy.toInt()} m, vieille de " +
                    "${(debut - derniere.time) / 1000} s (${derniere.provider})"
            }
            val message = JSONObject().put("type", "sos").put("id", id).put("emis", debut)
                .put("de", reglages.nom).put("test", true).put("batterie", batterie())
            if (reglages.envoyerPosition && derniere != null) ajouterPosition(message, derniere)
            try {
                Ntfy.publier(reglages.serveurNtfy, reglages.sujet, message)
                etat = "Alerte $id envoyée en ${System.currentTimeMillis() - debut} ms, en attente d'accusé"
                reglages.journaliser("Alerte $id envoyée par ntfy (position ${if (message.has("lat")) "incluse" else "non incluse"})")
            } catch (e: Exception) {
                etat = "Échec de l'envoi : ${e.message}. Utilisez le SMS de secours."
                reglages.journaliser("Envoi de $id ÉCHOUÉ : ${e.message}")
                proposerSms = true
            }
            positionFraiche(id, debut)
        }
        travail.execute { attendreAccuses(id, debut) }
    }

    private fun attendreAccuses(id: String, debut: Long) {
        val depuis = debut / 1000 - 5
        while (System.currentTimeMillis() - debut < ATTENTE_MS && !vue) {
            try {
                for (accuse in Ntfy.relever(reglages.serveurNtfy, Ntfy.sujetRetour(reglages.sujet), depuis)) {
                    if (accuse.optString("id") != id) continue
                    val apres = (accuse.optLong("heure") - debut) / 1000.0
                    when (accuse.optString("type")) {
                        "recu" -> if (!recue) { recue = true; reglages.journaliser("Accusé : $id reçue ${apres} s après l'envoi") }
                        "vu" -> if (!vue) { vue = true; reglages.journaliser("Accusé : $id vue ${apres} s après l'envoi") }
                    }
                }
            } catch (_: Exception) {
                // Réseau capricieux : on réessaie au tour suivant.
            }
            if (!vue) Thread.sleep(2_000)
        }
        etat = when {
            vue -> "Alerte vue par votre partenaire"
            recue -> "Reçue mais pas vue en 60 s : proposez le SMS ou le 112"
            else -> "Aucun accusé en 60 s : proposez le SMS ou le 112"
        }
        if (!vue) proposerSms = true
        enCours = false
    }

    @SuppressLint("MissingPermission")
    private fun positionFraiche(id: String, debut: Long) {
        if (!Diagnostic.accorde(context, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            detailPosition = "Permission de position refusée"
            return
        }
        val lm = context.getSystemService(LocationManager::class.java)
        val fournisseur = when {
            Build.VERSION.SDK_INT >= 31 && lm.hasProvider(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> LocationManager.NETWORK_PROVIDER
        }
        val demande = System.currentTimeMillis()
        lm.getCurrentLocation(fournisseur, CancellationSignal(), travail) { lieu ->
            val delai = (System.currentTimeMillis() - demande) / 1000.0
            if (lieu == null) {
                detailPosition += "\nPosition fraîche introuvable après $delai s ($fournisseur)"
                reglages.journaliser("Position fraîche introuvable après $delai s ($fournisseur)")
                return@getCurrentLocation
            }
            position = lieu
            val precise = Diagnostic.accorde(context, Manifest.permission.ACCESS_FINE_LOCATION)
            detailPosition = "Fraîche en $delai s : ±${lieu.accuracy.toInt()} m ($fournisseur, " +
                (if (precise) "position exacte" else "APPROXIMATIVE") + ")"
            reglages.journaliser("Position fraîche pour $id en $delai s : ±${lieu.accuracy.toInt()} m ($fournisseur)")
            if (reglages.envoyerPosition) {
                try {
                    val maj = JSONObject().put("type", "pos").put("id", id).put("emisPosition", lieu.time)
                    Ntfy.publier(reglages.serveurNtfy, reglages.sujet, ajouterPosition(maj, lieu))
                } catch (_: Exception) { }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun derniereConnue(): Location? {
        if (!Diagnostic.accorde(context, Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        val lm = context.getSystemService(LocationManager::class.java)
        return lm.allProviders.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun ajouterPosition(message: JSONObject, lieu: Location) = message
        .put("lat", lieu.latitude).put("lon", lieu.longitude).put("precision", lieu.accuracy.toInt())

    private fun batterie() = context.getSystemService(BatteryManager::class.java)
        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    fun texteSms(): String {
        val lieu = position
        val lien = if (lieu != null) {
            String.format(
                Locale.ROOT, "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=18/%.6f/%.6f",
                lieu.latitude, lieu.longitude, lieu.latitude, lieu.longitude,
            ) + " (±${lieu.accuracy.toInt()} m)"
        } else {
            "position inconnue"
        }
        return "[TEST Cocooning] S.O.S de ${reglages.nom}. Position : $lien"
    }

    /** SMS direct si la permission est accordée, sinon l'appli SMS s'ouvre préremplie. */
    fun envoyerSms(): String {
        val numero = reglages.numeroSms
        if (numero.isBlank()) return "Renseignez d'abord un numéro"
        val texte = texteSms()
        return if (Diagnostic.accorde(context, Manifest.permission.SEND_SMS)) {
            try {
                val sms = context.getSystemService(SmsManager::class.java)
                sms.sendMultipartTextMessage(numero, null, sms.divideMessage(texte), null, null)
                reglages.journaliser("SMS de secours envoyé directement à $numero")
                "SMS envoyé"
            } catch (e: Exception) {
                reglages.journaliser("SMS direct ÉCHOUÉ : ${e.message}")
                ouvrirAppliSms(numero, texte)
            }
        } else {
            ouvrirAppliSms(numero, texte)
        }
    }

    private fun ouvrirAppliSms(numero: String, texte: String): String {
        context.startActivity(
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$numero")).putExtra("sms_body", texte)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        reglages.journaliser("Appli SMS ouverte préremplie (sans permission SEND_SMS)")
        return "Appli SMS ouverte"
    }

    companion object {
        private const val ATTENTE_MS = 60_000L
    }
}
