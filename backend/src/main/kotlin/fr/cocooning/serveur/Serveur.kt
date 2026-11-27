package fr.cocooning.serveur

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Démarrage : `cocooning-serveur [chemin/vers/server.conf]`. Sans argument, le fichier vient de
 * la variable `COCOONING_CONFIG`, sinon `config/server.conf` ; sans fichier, les valeurs par
 * défaut s'appliquent (écoute sur 127.0.0.1:8080, données dans ./donnees).
 */
fun main(arguments: Array<String>) {
    val journal = LoggerFactory.getLogger("fr.cocooning.serveur")
    val fichier = arguments.firstOrNull()?.let(::File) ?: ConfigServeur.fichierParDefaut()
    val config = ConfigServeur.charger(fichier)
    journal.info(
        "Cocooning : configuration {} ; écoute sur {}:{}",
        if (fichier.isFile) fichier.absolutePath else "par défaut", config.hote, config.port,
    )
    val services = Services(config, Base.ouvrir(config.cheminBase))
    Runtime.getRuntime().addShutdownHook(Thread { services.fermer() })
    embeddedServer(CIO, port = config.port, host = config.hote) { cocooning(services) }.start(wait = true)
}
