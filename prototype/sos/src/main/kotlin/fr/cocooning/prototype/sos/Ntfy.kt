package fr.cocooning.prototype.sos

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Client ntfy minimal (HTTP brut, sans bibliothèque). Le serveur ne voit que du JSON
 * de test ; le vrai Cocooning y mettra seulement un identifiant d'alerte chiffré.
 */
object Ntfy {
    /** Sujet des accusés « reçue » et « vue », renvoyés par le récepteur à l'émetteur. */
    fun sujetRetour(sujet: String) = "$sujet-retour"

    fun publier(serveur: String, sujet: String, contenu: JSONObject) {
        val connexion = URL("$serveur/${encoder(sujet)}").openConnection() as HttpURLConnection
        try {
            connexion.requestMethod = "POST"
            connexion.connectTimeout = 10_000
            connexion.readTimeout = 10_000
            connexion.doOutput = true
            connexion.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connexion.outputStream.use { it.write(contenu.toString().toByteArray()) }
            val code = connexion.responseCode
            if (code !in 200..299) error("ntfy a répondu $code")
        } finally {
            connexion.disconnect()
        }
    }

    /** Messages publiés depuis `depuisSecondes` (horodatage Unix), sans garder la connexion. */
    fun relever(serveur: String, sujet: String, depuisSecondes: Long): List<JSONObject> {
        val url = URL("$serveur/${encoder(sujet)}/json?poll=1&since=$depuisSecondes")
        val connexion = url.openConnection() as HttpURLConnection
        return try {
            connexion.connectTimeout = 10_000
            connexion.readTimeout = 10_000
            connexion.inputStream.bufferedReader().useLines { lignes ->
                lignes.mapNotNull { messageUtile(it) }.toList()
            }
        } finally {
            connexion.disconnect()
        }
    }

    /**
     * Ouvre le flux JSON et appelle `surMessage` pour chaque message, jusqu'à coupure.
     * ntfy.sh envoie un « keepalive » toutes les 45 s environ : au-delà de 100 s sans rien,
     * la connexion est considérée morte.
     */
    fun ecouter(serveur: String, sujet: String, depuis: String, surOuverture: () -> Unit, surMessage: (JSONObject) -> Unit) {
        val url = URL("$serveur/${encoder(sujet)}/json?since=${encoder(depuis)}")
        val connexion = url.openConnection() as HttpURLConnection
        try {
            connexion.connectTimeout = 15_000
            connexion.readTimeout = 100_000
            val lecteur: BufferedReader = connexion.inputStream.bufferedReader()
            surOuverture()
            while (true) {
                val ligne = lecteur.readLine() ?: break
                messageUtile(ligne)?.let(surMessage)
            }
        } finally {
            connexion.disconnect()
        }
    }

    /** Garde les événements « message » ; ajoute l'identifiant et l'heure ntfy au contenu. */
    private fun messageUtile(ligne: String): JSONObject? {
        if (ligne.isBlank()) return null
        val evenement = JSONObject(ligne)
        if (evenement.optString("event") != "message") return null
        val contenu = try {
            JSONObject(evenement.optString("message"))
        } catch (e: Exception) {
            return null
        }
        contenu.put("_ntfyId", evenement.optString("id"))
        contenu.put("_ntfyHeure", evenement.optLong("time"))
        return contenu
    }

    private fun encoder(texte: String) = URLEncoder.encode(texte, "UTF-8")
}
