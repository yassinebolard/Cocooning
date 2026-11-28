package fr.cocooning.serveur

import fr.cocooning.protocole.api.ReveilApi
import fr.cocooning.protocole.api.TrameTempsReel
import fr.cocooning.protocole.api.TypeReveil
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Réveil d'un appareil qui a du courrier (ENF-EVO-03). Le réveil ne porte aucun contenu, ni même
 * l'expéditeur (ENF-SEC-07) : le téléphone se réveille, relève sa boîte et déchiffre lui-même.
 */
fun interface Reveilleur {
    suspend fun reveiller(reveil: ReveilApi)
}

/**
 * Réveil par ntfy (UnifiedPush), auto-hébergé sur le Proxmox. La cible est le sujet choisi par le
 * téléphone, long et aléatoire. FCM viendra quand le projet Firebase existera (ADR 0004).
 */
class ReveilleurNtfy(private val config: ConfigServeur, private val client: HttpClient) : Reveilleur {
    private val journal = LoggerFactory.getLogger(ReveilleurNtfy::class.java)

    override suspend fun reveiller(reveil: ReveilApi) {
        when (reveil.type) {
            TypeReveil.Aucun -> Unit
            TypeReveil.Fcm -> journal.debug("Réveil FCM pas encore branché : l'appareil relèvera à sa prochaine connexion")
            TypeReveil.Ntfy -> {
                if (config.ntfyUrl.isBlank() || reveil.cible.isBlank()) return
                runCatching {
                    client.post("${config.ntfyUrl.trimEnd('/')}/${reveil.cible}") {
                        header("Priority", "high")
                        if (config.ntfyJeton.isNotBlank()) header("Authorization", "Bearer ${config.ntfyJeton}")
                        setBody("1")
                    }
                }.onFailure { journal.warn("Réveil ntfy impossible : {}", it.message) }
            }
        }
    }
}

/** Connexions WebSocket ouvertes, par appareil (plusieurs onglets ou reconnexions possibles). */
class TempsReel(private val json: Json) {
    private val connexions = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

    fun ajouter(appareil: String, session: DefaultWebSocketServerSession) {
        connexions.computeIfAbsent(appareil) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    fun retirer(appareil: String, session: DefaultWebSocketServerSession) {
        connexions[appareil]?.remove(session)
    }

    fun connecte(appareil: String): Boolean = !connexions[appareil].isNullOrEmpty()

    /** Envoie [trame] à toutes les connexions de l'appareil ; vrai si au moins une existait. */
    fun envoyer(scope: CoroutineScope, appareil: String, trame: TrameTempsReel): Boolean {
        val sessions = connexions[appareil].orEmpty().toList()
        val texte = json.encodeToString(TrameTempsReel.serializer(), trame)
        sessions.forEach { session -> scope.launch { runCatching { session.send(Frame.Text(texte)) } } }
        return sessions.isNotEmpty()
    }

    /** Ferme les connexions d'un appareil désactivé. */
    suspend fun fermer(appareil: String) {
        connexions.remove(appareil)?.forEach { runCatching { it.close() } }
    }
}
