package fr.cocooning.prototype.sos

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager

/**
 * Voie sans Google (plan B /e/OS, ENF-EVO-03) : un service au premier plan garde une
 * connexion ouverte au sujet ntfy. C'est le « dernier recours » du cahier ; il mesure aussi
 * ce que One UI et ColorOS font d'une connexion permanente (coupures, mise en veille).
 */
class EcouteNtfyService : Service() {
    @Volatile private var actif = false
    private var fil: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Les types de service n'existent qu'à partir d'Android 14 (OPPO sous Android 11 ou 12).
        if (android.os.Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        else startForeground(NOTIFICATION_ID, notification())
        if (fil?.isAlive != true) {
            actif = true
            fil = Thread(::boucle, "ecoute-ntfy").apply { start() }
        }
        // Relancé par le système s'il est tué : c'est justement ce qu'on veut observer.
        return START_STICKY
    }

    private fun boucle() {
        val reglages = Reglages(this)
        val power = getSystemService(PowerManager::class.java)
        var attente = 2_000L
        var coupures = 0
        var ouverteA = 0L
        reglages.journaliser("Écoute ntfy démarrée sur ${reglages.serveurNtfy}/${reglages.sujet}")
        while (actif) {
            try {
                // Au premier passage, on rattrape les 10 dernières minutes ; ensuite, depuis le dernier message vu.
                val depuis = reglages.dernierIdNtfy ?: "10m"
                Ntfy.ecouter(
                    reglages.serveurNtfy, reglages.sujet, depuis,
                    surOuverture = {
                        ouverteA = System.currentTimeMillis()
                        if (coupures > 0) reglages.journaliser("Écoute ntfy rétablie après $coupures coupure(s)")
                        attente = 2_000L
                    },
                    surMessage = { message ->
                        reglages.dernierIdNtfy = message.optString("_ntfyId")
                        AlerteRecue.traiter(this, message, "ntfy")
                    },
                )
                throw IllegalStateException("flux fermé par le serveur")
            } catch (e: Exception) {
                if (!actif) break
                coupures++
                val duree = if (ouverteA > 0) (System.currentTimeMillis() - ouverteA) / 1000 else 0
                reglages.journaliser(
                    "Écoute ntfy coupée (${e.javaClass.simpleName}: ${e.message}) après $duree s, " +
                        "doze=${Diagnostic.oui(power.isDeviceIdleMode)}, nouvel essai dans ${attente / 1000} s",
                )
                ouverteA = 0
                try {
                    Thread.sleep(attente)
                } catch (_: InterruptedException) {
                    break
                }
                attente = (attente * 2).coerceAtMost(60_000L)
            }
        }
    }

    override fun onDestroy() {
        actif = false
        fil?.interrupt()
        Reglages(this).journaliser("Écoute ntfy arrêtée (service détruit)")
        super.onDestroy()
    }

    private fun notification(): Notification {
        val ouvrir = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, Canaux.idEcoute())
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Prototype S.O.S : à l'écoute")
            .setContentText("Connexion ntfy ouverte")
            .setContentIntent(ouvrir)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 7

        fun demarrer(context: Context) {
            try {
                context.startForegroundService(Intent(context, EcouteNtfyService::class.java))
            } catch (e: Exception) {
                Reglages(context).journaliser("Écoute ntfy NON démarrée (${e.javaClass.simpleName}: ${e.message})")
            }
        }

        fun arreter(context: Context) {
            context.stopService(Intent(context, EcouteNtfyService::class.java))
        }
    }
}
