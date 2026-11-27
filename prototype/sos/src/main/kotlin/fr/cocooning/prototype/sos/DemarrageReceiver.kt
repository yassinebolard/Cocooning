package fr.cocooning.prototype.sos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Relance l'écoute ntfy après un redémarrage du téléphone ou une mise à jour du prototype. */
class DemarrageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reglages = Reglages(context)
        reglages.journaliser("Reçu ${intent.action?.substringAfterLast('.')}")
        if (reglages.ecouteActive) EcouteNtfyService.demarrer(context)
    }
}
