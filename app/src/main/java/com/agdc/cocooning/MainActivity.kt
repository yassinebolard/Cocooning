package com.agdc.cocooning

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.agdc.cocooning.ui.RacineCocooning
import fr.cocooning.designsystem.CocooningTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Barres système transparentes ; leurs icônes suivent le thème clair ou sombre du système.
        enableEdgeToEdge()
        val conteneur = (application as CocooningApplication).conteneur
        setContent {
            CocooningTheme {
                RacineCocooning(conteneur, onRedemarrer = ::redemarrer)
            }
        }
    }

    /**
     * Relance l'appli dans un nouveau processus, après une restauration : Room et les préférences
     * doivent rouvrir les fichiers remis en place.
     */
    private fun redemarrer() {
        startActivity(Intent.makeRestartActivityTask(ComponentName(this, MainActivity::class.java)))
        Runtime.getRuntime().exit(0)
    }
}
