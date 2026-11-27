package fr.cocooning.prototype.sos

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject

/** Voie Google : message FCM de données, envoyé en priorité haute par le simulateur. */
class ProtoFirebaseService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val reglages = Reglages(this)
        reglages.jetonFcm = token
        reglages.journaliser("Nouveau jeton FCM : $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // FCM déclasse une priorité haute en normale si l'appli en abuse sans rien afficher :
        // l'écart entre les deux valeurs est la mesure qui compte ici.
        val detail = "(priorité ${nom(message.priority)}, demandée ${nom(message.originalPriority)})"
        val contenu = JSONObject()
        message.data.forEach { (cle, valeur) ->
            contenu.put(cle, valeur.toLongOrNull() ?: valeur.toDoubleOrNull() ?: valeur)
        }
        contenu.put("id", message.data["id"] ?: message.messageId ?: "fcm")
        AlerteRecue.traiter(this, contenu, "FCM", detail)
    }

    private fun nom(priorite: Int) = when (priorite) {
        RemoteMessage.PRIORITY_HIGH -> "haute"
        RemoteMessage.PRIORITY_NORMAL -> "normale"
        else -> "inconnue"
    }
}
