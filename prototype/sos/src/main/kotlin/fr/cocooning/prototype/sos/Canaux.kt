package fr.cocooning.prototype.sos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager

/**
 * Canaux de notification.
 *
 * Hypothèse du cahier (6.2) à vérifier : setBypassDnd(true) n'est retenu par Android que si
 * l'appli a l'accès Ne pas déranger au moment où le canal est créé. Un canal existant ne peut
 * pas être modifié par l'appli : on le supprime et on en recrée un avec un nouvel identifiant.
 */
object Canaux {
    private const val ECOUTE = "ecoute"
    private const val SECOURS = "sos_secours"

    fun idAlerte(context: Context) = "sos_alerte_v" + Reglages(context).versionCanal
    fun idEcoute() = ECOUTE
    fun idSecours() = SECOURS

    fun creer(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        // Canal principal : la notification de la sonnerie elle-même. Sans son propre,
        // c'est SonnerieService qui joue l'alarme (flux alarme, indépendant du canal).
        nm.createNotificationChannel(
            NotificationChannel(idAlerte(context), "Alerte S.O.S", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerte S.O.S du partenaire, plein écran"
                setBypassDnd(true)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )

        // Canal de secours : utilisé si le service de sonnerie ne peut pas démarrer.
        // Le son vient alors du canal, sur le flux alarme.
        nm.createNotificationChannel(
            NotificationChannel(SECOURS, "Alerte S.O.S (secours)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Utilisé si la sonnerie principale ne démarre pas"
                setBypassDnd(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )

        nm.createNotificationChannel(
            NotificationChannel(ECOUTE, "Écoute des alertes", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Connexion permanente au serveur ntfy"
                setShowBadge(false)
            },
        )
    }

    /** Recrée le canal principal (nouvel identifiant) pour que Android relise setBypassDnd. */
    fun recreerCanalAlerte(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val reglages = Reglages(context)
        nm.deleteNotificationChannel(idAlerte(context))
        nm.deleteNotificationChannel(SECOURS)
        reglages.versionCanal = reglages.versionCanal + 1
        creer(context)
        val canal = nm.getNotificationChannel(idAlerte(context))
        reglages.journaliser(
            "Canal recréé (${idAlerte(context)}) : ignore NPD=${Diagnostic.oui(canal.canBypassDnd())}, " +
                "accès NPD=${Diagnostic.oui(nm.isNotificationPolicyAccessGranted)}",
        )
    }
}
