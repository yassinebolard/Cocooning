package fr.cocooning.prototype.sos

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Fait sonner l'alerte : volume alarme au maximum, son en boucle sur le flux alarme
 * (USAGE_ALARM, que le silencieux et le vibreur ne coupent pas), vibration, et notification
 * plein écran. S'arrête sur « J'ai vu », « Arrêter » ou au bout de 5 minutes (prototype).
 */
class SonnerieService : Service() {
    private var lecteur: MediaPlayer? = null
    private var verrou: PowerManager.WakeLock? = null
    private val minuterie = Handler(Looper.getMainLooper())
    private var idAlerte: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reglages = Reglages(this)
        when (intent?.action) {
            ACTION_VU, ACTION_ARRETER -> {
                val id = idAlerte.ifEmpty {
                    reglages.alerteCourante?.let { org.json.JSONObject(it).optString("id") } ?: ""
                }
                if (intent.action == ACTION_VU && id.isNotEmpty()) AlerteRecue.accuser(this, id, "vu")
                getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID + 1)
                reglages.journaliser(if (intent.action == ACTION_VU) "« J'ai vu » : sonnerie coupée" else "Sonnerie arrêtée")
                arreter()
                return START_NOT_STICKY
            }
        }
        idAlerte = intent?.getStringExtra(EXTRA_ID) ?: ""
        val notification = notificationAlerte(this, Canaux.idAlerte(this), idAlerte)
        // Les types de service n'existent qu'à partir d'Android 14 (OPPO sous Android 11 ou 12).
        if (android.os.Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, notification)

        verrou = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cocooning:sonnerie")
            .apply { acquire(DUREE_MAX_MS + 10_000) }
        monterVolume()
        jouer()
        vibrer()
        minuterie.postDelayed({
            reglages.journaliser("Sonnerie arrêtée après 5 min sans réponse")
            arreter()
        }, DUREE_MAX_MS)
        return START_NOT_STICKY
    }

    private fun monterVolume() {
        val reglages = Reglages(this)
        val audio = getSystemService(AudioManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        if (reglages.volumeAvant < 0) {
            reglages.volumeAvant = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            reglages.modeSonnerieAvant = audio.ringerMode
            reglages.filtreAvant = nm.currentInterruptionFilter
        }
        // 1) Repasser en mode sonnerie, seulement avec l'accès Ne pas déranger (hypothèse 6.2).
        // Observé sur l'émulateur Android 17 : cet appel sort aussi du Ne pas déranger, même en
        // silence total. Il passe donc avant le volume, que le silence total bloquerait à 0.
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL && nm.isNotificationPolicyAccessGranted) {
            try {
                audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
            } catch (e: SecurityException) {
                reglages.journaliser("setRingerMode refusé : ${e.message}")
            }
        }
        // 2) Volume alarme au maximum.
        try {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } catch (e: SecurityException) {
            reglages.journaliser("Volume alarme non modifiable : ${e.message}")
        }
        reglages.journaliser(
            "Volume alarme ${audio.getStreamVolume(AudioManager.STREAM_ALARM)}/" +
                "${audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)}, mode " +
                Diagnostic.nomModeSonnerie(audio.ringerMode) + ", NPD " + Diagnostic.nomFiltre(nm.currentInterruptionFilter),
        )
    }

    private fun jouer() {
        val sources = listOfNotNull(
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (source in sources) {
            try {
                lecteur = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    setDataSource(this@SonnerieService, source)
                    isLooping = true
                    prepare()
                    start()
                }
                Reglages(this).journaliser("Son lancé sur le flux alarme ($source)")
                return
            } catch (e: Exception) {
                lecteur?.release()
                lecteur = null
            }
        }
        Reglages(this).journaliser("AUCUN son n'a pu être joué")
    }

    private fun vibrer() {
        val vibreur = vibreur()
        @Suppress("DEPRECATION")
        vibreur.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
        )
    }

    private fun arreter() {
        minuterie.removeCallbacksAndMessages(null)
        lecteur?.run { try { stop() } catch (_: Exception) {}; release() }
        lecteur = null
        vibreur().cancel()
        retablir(this)
        verrou?.takeIf { it.isHeld }?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** VibratorManager n'existe qu'à partir d'Android 12 (l'OPPO Reno 8 Lite peut être en 11). */
    private fun vibreur(): Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Vibrator::class.java)
    }

    override fun onDestroy() {
        lecteur?.release()
        retablir(this)
        verrou?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_VU = "fr.cocooning.prototype.sos.VU"
        const val ACTION_ARRETER = "fr.cocooning.prototype.sos.ARRETER"
        const val EXTRA_ID = "id"
        private const val NOTIFICATION_ID = 42
        private const val DUREE_MAX_MS = 5 * 60_000L

        fun demarrer(context: Context, id: String) {
            val intention = Intent(context, SonnerieService::class.java).putExtra(EXTRA_ID, id)
            try {
                context.startForegroundService(intention)
            } catch (e: Exception) {
                // Démarrage refusé (arrière-plan restreint, FCM déclassé...) : la notification
                // de secours sonne par son canal, sur le flux alarme, sans monter le volume.
                Reglages(context).journaliser("Service de sonnerie REFUSÉ (${e.javaClass.simpleName}) : canal de secours")
                val notification = notificationAlerte(context, Canaux.idSecours(), id)
                notification.flags = notification.flags or Notification.FLAG_INSISTENT
                context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1, notification)
            }
        }

        fun commande(context: Context, action: String) {
            context.startService(Intent(context, SonnerieService::class.java).setAction(action))
        }

        /** Rétablit volume et mode sonnerie d'origine (aussi au démarrage, après un plantage). */
        fun retablir(context: Context) {
            val reglages = Reglages(context)
            if (reglages.volumeAvant < 0) return
            val audio = context.getSystemService(AudioManager::class.java)
            try {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, reglages.volumeAvant, 0)
                if (reglages.modeSonnerieAvant >= 0 && audio.ringerMode != reglages.modeSonnerieAvant) {
                    audio.ringerMode = reglages.modeSonnerieAvant
                }
                // Si l'alerte a fait sortir du Ne pas déranger, on tente de le remettre et on note le résultat.
                val nm = context.getSystemService(NotificationManager::class.java)
                val filtreAvant = reglages.filtreAvant
                if (filtreAvant > 0 && nm.currentInterruptionFilter != filtreAvant && nm.isNotificationPolicyAccessGranted) {
                    nm.setInterruptionFilter(filtreAvant)
                    reglages.journaliser(
                        "Ne pas déranger à rétablir : ${Diagnostic.nomFiltre(filtreAvant)}, obtenu " +
                            Diagnostic.nomFiltre(nm.currentInterruptionFilter),
                    )
                }
            } catch (e: SecurityException) {
                reglages.journaliser("Rétablissement partiel du son : ${e.message}")
            }
            reglages.volumeAvant = -1
            reglages.modeSonnerieAvant = -1
            reglages.filtreAvant = -1
        }

        fun retablirSiInterrompu(context: Context) = retablir(context)

        fun notificationAlerte(context: Context, canal: String, id: String): Notification {
            val pleinEcran = PendingIntent.getActivity(
                context, 0, Intent(context, AlerteActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val vu = PendingIntent.getService(
                context, 1, Intent(context, SonnerieService::class.java).setAction(ACTION_VU),
                PendingIntent.FLAG_IMMUTABLE,
            )
            return Notification.Builder(context, canal)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("S.O.S TEST de votre partenaire")
                .setContentText("Touchez pour voir l'alerte ($id)")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setFullScreenIntent(pleinEcran, true)
                .setContentIntent(pleinEcran)
                .addAction(Notification.Action.Builder(null, "J'ai vu", vu).build())
                .build()
        }
    }
}
