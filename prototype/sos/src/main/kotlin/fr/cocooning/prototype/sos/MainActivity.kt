package fr.cocooning.prototype.sos

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.util.UUID

class MainActivity : ComponentActivity() {
    /** Incrémenté au retour sur l'écran : l'assistant relit alors l'état du système. */
    private var rafraichir by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.getStringExtra("sujet")?.let { Reglages(this).sujet = it; Reglages(this).dernierIdNtfy = null }
        val envoi = EnvoiAlerte(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    EcranPrincipal(this, envoi, rafraichir)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rafraichir++
    }

    fun ouvrir(intention: Intent) {
        try {
            startActivity(intention)
        } catch (e: Exception) {
            Toast.makeText(this, "Écran indisponible sur ce téléphone : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun copier(texte: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("prototype", texte))
        Toast.makeText(this, "Copié", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun EcranPrincipal(activite: MainActivity, envoi: EnvoiAlerte, rafraichir: Int) {
    val reglages = remember { Reglages(activite) }
    var maj by remember { mutableIntStateOf(0) }
    val diagnostic = remember(rafraichir, maj) { Diagnostic.relever(activite) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { maj++ }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Prototype S.O.S", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Application jetable du jalon J3. Les alertes sont marquées TEST.", fontSize = 13.sp)

        // Appairage : les deux téléphones doivent avoir le même sujet.
        Titre("1. Appairage")
        var nom by remember { mutableStateOf(reglages.nom) }
        OutlinedTextField(nom, { nom = it; reglages.nom = it }, label = { Text("Nom de ce téléphone") }, modifier = Modifier.fillMaxWidth())
        var sujet by remember { mutableStateOf(reglages.sujet) }
        OutlinedTextField(
            sujet, { sujet = it; reglages.sujet = it; reglages.dernierIdNtfy = null },
            label = { Text("Code commun (sujet ntfy)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        var serveur by remember { mutableStateOf(reglages.serveurNtfy) }
        OutlinedTextField(serveur, { serveur = it; reglages.serveurNtfy = it }, label = { Text("Serveur ntfy") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row {
            TextButton(onClick = { activite.copier(reglages.sujet) }) { Text("Copier le code") }
            TextButton(onClick = {
                activite.ouvrir(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, reglages.sujet), "Partager le code"))
            }) { Text("Partager le code") }
        }

        Titre("2. Réception")
        var ecoute by remember { mutableStateOf(reglages.ecouteActive) }
        Ligne("Écoute ntfy permanente (voie sans Google)") {
            Switch(ecoute, {
                ecoute = it; reglages.ecouteActive = it
                if (it) EcouteNtfyService.demarrer(activite) else EcouteNtfyService.arreter(activite)
            })
        }
        if (ProtoApp.firebaseConfigure) {
            val jeton = reglages.jetonFcm
            Text("FCM configuré. Jeton : ${jeton?.take(24) ?: "en attente"}...", fontSize = 13.sp)
            if (jeton != null) TextButton(onClick = { activite.copier(jeton) }) { Text("Copier le jeton FCM") }
        } else {
            Text("FCM non configuré (voir docs/prototype-sos.md) : seule la voie ntfy est active.", fontSize = 13.sp)
        }

        Titre("3. Assistant de réglages")
        Etat("Notifications autorisées", diagnostic.notificationsAutorisees) {
            if (Build.VERSION.SDK_INT >= 33) permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            else activite.ouvrir(Diagnostic.ecranNotificationsAppli(activite))
        }
        Etat("Plein écran sur écran verrouillé", diagnostic.pleinEcranAutorise) {
            activite.ouvrir(Diagnostic.ecranPleinEcran(activite))
        }
        Etat("Accès Ne pas déranger", diagnostic.accesNePasDeranger) {
            activite.ouvrir(Diagnostic.ecranAccesNePasDeranger())
        }
        Etat("Canal S.O.S ignore Ne pas déranger", diagnostic.canalIgnoreNePasDeranger) {
            activite.ouvrir(Diagnostic.ecranCanal(activite))
        }
        TextButton(onClick = { Canaux.recreerCanalAlerte(activite); maj++ }) {
            Text("Recréer le canal S.O.S (après l'accès Ne pas déranger)")
        }
        Etat("Optimisation batterie désactivée", diagnostic.sansOptimisationBatterie) {
            activite.ouvrir(Diagnostic.demandeSansOptimisation(activite))
        }
        Etat("Arrière-plan non restreint", !diagnostic.arrierePlanRestreint) {
            activite.ouvrir(Diagnostic.ecranDetailsAppli(activite))
        }
        Etat("Position exacte", diagnostic.positionPrecise) {
            permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        Etat("Envoi direct de SMS (facultatif)", diagnostic.smsAutorise) {
            permissions.launch(arrayOf(Manifest.permission.SEND_SMS))
        }
        Text(
            "Non lisibles par l'appli, à vérifier à la main : Samsung « Applications jamais en veille » " +
                "(Batterie > Limites d'utilisation en arrière-plan), exception de Cocooning dans chaque mode " +
                "Ne pas déranger, exception Alarmes.",
            fontSize = 13.sp,
        )
        Row {
            TextButton(onClick = { activite.ouvrir(Diagnostic.ecranDetailsAppli(activite)) }) { Text("Infos de l'appli") }
            TextButton(onClick = { activite.ouvrir(Diagnostic.ecranNePasDeranger()) }) { Text("Ne pas déranger") }
        }
        Text("État actuel : ${diagnostic.resume()}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        OutlinedButton(onClick = {
            Toast.makeText(activite, "Verrouillez le téléphone : sonnerie dans 15 s", Toast.LENGTH_LONG).show()
            reglages.journaliser("Essai local programmé dans 15 s")
            Handler(Looper.getMainLooper()).postDelayed({
                val faux = JSONObject().put("type", "sos").put("id", "local-" + UUID.randomUUID().toString().take(4))
                    .put("emis", System.currentTimeMillis()).put("de", "essai local").put("test", true)
                AlerteRecue.traiter(activite.applicationContext, faux, "essai local")
            }, 15_000)
        }) { Text("Essai local : sonner dans 15 s (sans réseau)") }

        Titre("4. Envoyer une alerte TEST au partenaire")
        var numero by remember { mutableStateOf(reglages.numeroSms) }
        OutlinedTextField(numero, { numero = it; reglages.numeroSms = it }, label = { Text("Numéro pour le SMS de secours") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        var avecPosition by remember { mutableStateOf(reglages.envoyerPosition) }
        Ligne("Joindre la position au message ntfy (sinon : SMS seulement)") {
            Switch(avecPosition, { avecPosition = it; reglages.envoyerPosition = it })
        }
        Button(
            onClick = { envoi.envoyer() }, enabled = !envoi.enCours, modifier = Modifier.fillMaxWidth(),
        ) { Text("Envoyer une alerte TEST", fontSize = 18.sp) }
        Text(envoi.etat)
        if (envoi.detailPosition.isNotEmpty()) Text(envoi.detailPosition, fontSize = 13.sp)
        Text("Reçue : ${Diagnostic.oui(envoi.recue)}   Vue : ${Diagnostic.oui(envoi.vue)}")
        if (envoi.proposerSms) {
            Text("Pas de « J'ai vu » en 60 s (EF-SOS-08) :", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold)
            Text(envoi.texteSms(), fontSize = 12.sp)
            Row {
                Button(onClick = { Toast.makeText(activite, envoi.envoyerSms(), Toast.LENGTH_LONG).show() }) { Text("SMS de secours") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { activite.ouvrir(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }) {
                    Text("Composer le 112")
                }
            }
            Text("« Composer » ouvre seulement le clavier, rien n'est appelé.", fontSize = 12.sp)
        }

        Titre("5. Journal")
        Row {
            TextButton(onClick = { maj++ }) { Text("Actualiser") }
            TextButton(onClick = { activite.copier(reglages.journal) }) { Text("Copier") }
            TextButton(onClick = { reglages.viderJournal(); maj++ }) { Text("Vider") }
        }
        val journal = remember(maj, rafraichir, envoi.etat) { reglages.journal }
        Text(journal.take(8_000).ifEmpty { "(vide)" }, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Titre(texte: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(texte, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Ligne(libelle: String, controle: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(libelle, Modifier.weight(1f))
        controle()
    }
}

@Composable
private fun Etat(libelle: String, ok: Boolean, regler: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✓" else "✗", color = if (ok) Color(0xFF2E7D32) else Color(0xFFB3261E), fontSize = 20.sp, modifier = Modifier.width(28.dp))
        Text(libelle, Modifier.weight(1f))
        if (!ok) TextButton(onClick = regler) { Text("Régler") }
    }
}
