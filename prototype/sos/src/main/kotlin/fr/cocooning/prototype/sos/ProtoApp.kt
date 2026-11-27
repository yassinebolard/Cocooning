package fr.cocooning.prototype.sos

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging

class ProtoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Canaux.creer(this)
        SonnerieService.retablirSiInterrompu(this)
        initialiserFirebase()
        if (Reglages(this).ecouteActive) EcouteNtfyService.demarrer(this)
    }

    /**
     * Firebase sans le plugin google-services : les identifiants viennent de local.properties.
     * Absents, la voie FCM reste simplement inactive.
     */
    private fun initialiserFirebase() {
        if (!firebaseConfigure) return
        val reglages = Reglages(this)
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(
                    this,
                    FirebaseOptions.Builder()
                        .setApiKey(BuildConfig.FIREBASE_API_KEY)
                        .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                        .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                        .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                        .build(),
                )
            }
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { jeton ->
                    if (jeton != reglages.jetonFcm) reglages.journaliser("Jeton FCM obtenu : $jeton")
                    reglages.jetonFcm = jeton
                }
                .addOnFailureListener { reglages.journaliser("Jeton FCM impossible : ${it.message}") }
        } catch (e: Exception) {
            reglages.journaliser("Firebase non initialisé : ${e.message}")
        }
    }

    companion object {
        val firebaseConfigure: Boolean get() = BuildConfig.FIREBASE_APP_ID.isNotBlank()
    }
}
