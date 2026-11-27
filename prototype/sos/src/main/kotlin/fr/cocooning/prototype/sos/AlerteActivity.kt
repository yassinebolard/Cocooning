package fr.cocooning.prototype.sos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Locale

/** Écran plein écran de l'alerte reçue, y compris sur l'écran verrouillé (showWhenLocked). */
class AlerteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val reglages = Reglages(this)
        reglages.journaliser("Écran d'alerte affiché")
        setContent {
            MaterialTheme {
                var alerte by remember { mutableStateOf(reglages.alerteCourante?.let(::JSONObject)) }
                // La position peut arriver après l'alerte : on relit l'état chaque seconde.
                LaunchedEffect(Unit) {
                    while (true) {
                        alerte = reglages.alerteCourante?.let(::JSONObject)
                        delay(1_000)
                    }
                }
                EcranAlerte(
                    alerte = alerte,
                    surVu = {
                        SonnerieService.commande(this, SonnerieService.ACTION_VU)
                    },
                    surFermer = {
                        SonnerieService.commande(this, SonnerieService.ACTION_ARRETER)
                        finish()
                    },
                    surCarte = { lat, lon ->
                        val uri = Uri.parse(String.format(Locale.ROOT, "geo:%.6f,%.6f?q=%.6f,%.6f", lat, lon, lat, lon))
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (_: Exception) { }
                    },
                )
            }
        }
    }
}

@Composable
private fun EcranAlerte(
    alerte: JSONObject?,
    surVu: () -> Unit,
    surFermer: () -> Unit,
    surCarte: (Double, Double) -> Unit,
) {
    var vu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Color(0xFFB3261E)).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("S.O.S TEST", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        if (alerte == null) {
            Text("Aucune alerte en cours", color = Color.White)
        } else {
            val delai = (alerte.optLong("_recu") - alerte.optLong("emis")) / 1000.0
            Text("De : ${alerte.optString("de")}", color = Color.White, fontSize = 22.sp)
            Text(
                "Reçue par ${alerte.optString("_voie")} en $delai s. Batterie émetteur : ${alerte.optInt("batterie")} %",
                color = Color.White,
            )
            if (alerte.has("lat")) {
                val lat = alerte.getDouble("lat")
                val lon = alerte.getDouble("lon")
                Text(
                    String.format(Locale.FRANCE, "Position : %.5f, %.5f (±%d m)", lat, lon, alerte.optInt("precision")),
                    color = Color.White,
                )
                OutlinedButton(onClick = { surCarte(lat, lon) }) { Text("Ouvrir la carte", color = Color.White) }
            } else {
                Text("Position non transmise (réglage du prototype)", color = Color.White)
            }
            Text("Diagnostic à la réception : ${alerte.optString("_diagnostic")}", color = Color.White, fontSize = 12.sp)
        }
        Button(
            onClick = { vu = true; surVu() },
            enabled = !vu,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB3261E)),
        ) { Text(if (vu) "Vu, l'émetteur est prévenu" else "J'ai vu", fontSize = 20.sp) }
        OutlinedButton(onClick = surFermer, modifier = Modifier.fillMaxWidth()) {
            Text("Arrêter et fermer", color = Color.White)
        }
    }
}
