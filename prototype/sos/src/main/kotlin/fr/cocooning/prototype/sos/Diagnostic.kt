package fr.cocooning.prototype.sos

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Photographie de tout ce qui décide si l'alerte sonne : relevée à chaque réception
 * et affichée dans l'assistant. Ce que l'appli ne peut pas lire (exceptions Ne pas déranger
 * de One UI, « Applications jamais en veille ») est signalé comme tel.
 */
data class Diagnostic(
    val notificationsAutorisees: Boolean,
    val pleinEcranAutorise: Boolean,
    val accesNePasDeranger: Boolean,
    val canalIgnoreNePasDeranger: Boolean,
    val canalImportance: Int,
    val sansOptimisationBatterie: Boolean,
    val arrierePlanRestreint: Boolean,
    val compartimentVeille: Int,
    val economieEnergie: Boolean,
    val veilleProfonde: Boolean,
    val filtreInterruption: Int,
    val alarmesPermisesParNePasDeranger: Boolean?,
    val modeSonnerie: Int,
    val volumeAlarme: Int,
    val volumeAlarmeMax: Int,
    val positionPrecise: Boolean,
    val positionApproximative: Boolean,
    val smsAutorise: Boolean,
    val batterie: Int,
) {
    /** Une ligne compacte pour le journal. */
    fun resume(): String = buildString {
        append("NPD=").append(nomFiltre(filtreInterruption))
        alarmesPermisesParNePasDeranger?.let { append(if (it) " (alarmes permises)" else " (alarmes BLOQUÉES)") }
        append(", sonnerie=").append(nomModeSonnerie(modeSonnerie))
        append(", vol. alarme=").append(volumeAlarme).append('/').append(volumeAlarmeMax)
        append(", accès NPD=").append(oui(accesNePasDeranger))
        append(", canal ignore NPD=").append(oui(canalIgnoreNePasDeranger))
        append(", plein écran=").append(oui(pleinEcranAutorise))
        append(", batterie libre=").append(oui(sansOptimisationBatterie))
        append(", restreint=").append(oui(arrierePlanRestreint))
        append(", veille=").append(nomCompartiment(compartimentVeille))
        append(", éco=").append(oui(economieEnergie))
        append(", doze=").append(oui(veilleProfonde))
        append(", batt.=").append(batterie).append('%')
    }

    companion object {
        fun relever(context: Context): Diagnostic {
            val nm = context.getSystemService(NotificationManager::class.java)
            val audio = context.getSystemService(AudioManager::class.java)
            val power = context.getSystemService(PowerManager::class.java)
            val activite = context.getSystemService(ActivityManager::class.java)
            val usage = context.getSystemService(UsageStatsManager::class.java)
            val canal = nm.getNotificationChannel(Canaux.idAlerte(context))
            val alarmesPermises = try {
                if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    nm.notificationPolicy.priorityCategories and
                        NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS != 0
                } else null
            } catch (e: SecurityException) {
                null
            }
            return Diagnostic(
                notificationsAutorisees = nm.areNotificationsEnabled(),
                pleinEcranAutorise = if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true,
                accesNePasDeranger = nm.isNotificationPolicyAccessGranted,
                canalIgnoreNePasDeranger = canal?.canBypassDnd() == true,
                canalImportance = canal?.importance ?: -1,
                sansOptimisationBatterie = power.isIgnoringBatteryOptimizations(context.packageName),
                arrierePlanRestreint = activite.isBackgroundRestricted,
                compartimentVeille = usage.appStandbyBucket,
                economieEnergie = power.isPowerSaveMode,
                veilleProfonde = power.isDeviceIdleMode,
                filtreInterruption = nm.currentInterruptionFilter,
                alarmesPermisesParNePasDeranger = alarmesPermises,
                modeSonnerie = audio.ringerMode,
                volumeAlarme = audio.getStreamVolume(AudioManager.STREAM_ALARM),
                volumeAlarmeMax = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                positionPrecise = accorde(context, Manifest.permission.ACCESS_FINE_LOCATION),
                positionApproximative = accorde(context, Manifest.permission.ACCESS_COARSE_LOCATION),
                smsAutorise = accorde(context, Manifest.permission.SEND_SMS),
                batterie = context.getSystemService(BatteryManager::class.java)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            )
        }

        fun accorde(context: Context, permission: String) =
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

        // Intentions vers les écrans de réglages, utilisées par l'assistant.
        fun ecranPleinEcran(context: Context) =
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName))

        fun ecranAccesNePasDeranger() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

        fun ecranCanal(context: Context) = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, Canaux.idAlerte(context))

        fun ecranNotificationsAppli(context: Context) = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        @Suppress("BatteryLife")
        fun demandeSansOptimisation(context: Context) =
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName))

        fun ecranDetailsAppli(context: Context) =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))

        fun ecranNePasDeranger() = Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)

        fun oui(b: Boolean) = if (b) "oui" else "NON"

        fun nomFiltre(f: Int) = when (f) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "désactivé"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "prioritaire"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarmes seules"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "SILENCE TOTAL"
            else -> "inconnu($f)"
        }

        fun nomModeSonnerie(m: Int) = when (m) {
            AudioManager.RINGER_MODE_NORMAL -> "sonnerie"
            AudioManager.RINGER_MODE_VIBRATE -> "vibreur"
            AudioManager.RINGER_MODE_SILENT -> "silencieux"
            else -> "inconnu($m)"
        }

        fun nomCompartiment(c: Int) = when (c) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working_set"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
            UsageStatsManager.STANDBY_BUCKET_RARE -> "RARE"
            UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "RESTRICTED"
            else -> "exempt($c)"
        }
    }
}
