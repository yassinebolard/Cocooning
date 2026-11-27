package com.agdc.cocooning

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.agdc.cocooning.ui.CocooningApp
import fr.cocooning.designsystem.CocooningTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Barres système transparentes ; leurs icônes suivent le thème clair ou sombre du système.
        enableEdgeToEdge()
        val conteneur = (application as CocooningApplication).conteneur
        setContent {
            CocooningTheme {
                CocooningApp(conteneur)
            }
        }
    }
}
