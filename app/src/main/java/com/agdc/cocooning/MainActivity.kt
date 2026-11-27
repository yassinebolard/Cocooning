package com.agdc.cocooning

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.agdc.cocooning.demo.EcranDemo
import fr.cocooning.designsystem.CocooningTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemeSombre = isSystemInDarkTheme()
            // null : suit le système ; true ou false : choix manuel depuis l'écran de démonstration.
            var choixSombre by rememberSaveable { mutableStateOf<Boolean?>(null) }
            val sombre = choixSombre ?: systemeSombre

            // Icônes de la barre d'état lisibles quel que soit le thème choisi.
            DisposableEffect(sombre) {
                val style = if (sombre) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            CocooningTheme(sombre = sombre) {
                EcranDemo(
                    sombre = sombre,
                    onSombreChange = { choixSombre = it },
                )
            }
        }
    }
}
