package fr.cocooning.prototype.sos

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Point d'entrée commun des deux voies (FCM et ntfy) côté récepteur.
 *
 * Format des messages : {"type":"sos"|"pos", "id", "emis" (ms), "de", "test",
 * "lat", "lon", "precision", "batterie"}. "pos" met à jour la position d'une alerte en cours.
 */
object AlerteRecue {
    /** Au-delà, une alerte rattrapée après une coupure est journalisée mais ne sonne pas. */
    private const val AGE_MAX_MS = 15 * 60_000L

    private val reseau = Executors.newSingleThreadExecutor()

    fun traiter(context: Context, message: JSONObject, voie: String, detailVoie: String = "") {
        val reglages = Reglages(context)
        val recu = System.currentTimeMillis()
        val id = message.optString("id")
        when (message.optString("type")) {
            "sos" -> {
                val emis = message.optLong("emis", recu)
                val delai = recu - emis
                if (reglages.dejaTraitee(id)) {
                    reglages.journaliser("Doublon $id par $voie, reçu ${delai / 1000.0} s après l'envoi")
                    return
                }
                val diagnostic = Diagnostic.relever(context)
                reglages.journaliser(
                    "ALERTE $id reçue par $voie $detailVoie en ${delai / 1000.0} s. ${diagnostic.resume()}",
                )
                if (delai > AGE_MAX_MS) {
                    reglages.journaliser("Alerte $id trop ancienne (${delai / 60_000} min) : pas de sonnerie")
                    return
                }
                message.put("_recu", recu).put("_voie", voie).put("_diagnostic", diagnostic.resume())
                reglages.alerteCourante = message.toString()
                SonnerieService.demarrer(context, id)
                accuser(context, id, "recu")
            }
            "pos" -> {
                val courante = reglages.alerteCourante?.let(::JSONObject) ?: return
                if (courante.optString("id") != id) return
                for (cle in listOf("lat", "lon", "precision", "batterie", "emisPosition")) {
                    if (message.has(cle)) courante.put(cle, message.get(cle))
                }
                reglages.alerteCourante = courante.toString()
                reglages.journaliser("Position mise à jour pour $id (±${message.optInt("precision")} m)")
            }
        }
    }

    /** Accusé « recu » (automatique) ou « vu » (bouton J'ai vu), renvoyé par ntfy. */
    fun accuser(context: Context, id: String, etat: String) {
        val reglages = Reglages(context)
        reseau.execute {
            try {
                Ntfy.publier(
                    reglages.serveurNtfy,
                    Ntfy.sujetRetour(reglages.sujet),
                    JSONObject().put("type", etat).put("id", id).put("par", reglages.nom)
                        .put("heure", System.currentTimeMillis()),
                )
            } catch (e: Exception) {
                reglages.journaliser("Accusé « $etat » non envoyé : ${e.message}")
            }
        }
    }
}
