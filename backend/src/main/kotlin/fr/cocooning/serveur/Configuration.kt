package fr.cocooning.serveur

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.File
import java.time.Duration

/**
 * Tous les réglages du serveur (ENF-CONF-01). Les valeurs par défaut sont celles du cahier des
 * charges ; le fichier `config/server.conf` (modèle commenté : `config/server.conf.example`) les
 * remplace, et chaque valeur peut venir d'une variable d'environnement (`${?NOM}` dans le fichier).
 */
data class ConfigServeur(
    val hote: String = "127.0.0.1",
    val port: Int = 8080,
    /** Vrai derrière Nginx Proxy Manager : l'adresse du client est lue dans X-Forwarded-For. */
    val derriereProxy: Boolean = false,

    val cheminBase: String = "donnees/cocooning.db",
    val dossierMedias: String = "donnees/medias",

    val membresMax: Int = 2,
    val validiteInvitation: Duration = Duration.ofHours(24),

    val validiteJeton: Duration = Duration.ofMinutes(15),
    val validiteDefi: Duration = Duration.ofSeconds(60),

    val conservationMessages: Duration = Duration.ofDays(30),
    val tailleMaxEnveloppe: Int = 256 * 1024,
    val destinatairesMax: Int = 20,
    val releveMax: Int = 100,
    val preClesMaxParAppareil: Int = 200,

    val tailleMaxMedia: Long = 110_000_000,
    val tailleMorceau: Int = 8 * 1024 * 1024,
    val quotaMedias: Long = 60_000_000_000,
    val seuilAlerteMedias: Double = 0.80,
    val seuilBlocageMedias: Double = 0.95,
    val conservationMediasApresReception: Duration = Duration.ofDays(30),
    val conservationMediasMax: Duration = Duration.ofDays(90),
    val abandonEnvoiMedia: Duration = Duration.ofHours(24),

    val ntfyUrl: String = "",
    val ntfyJeton: String = "",

    val requetesParMinute: Int = 300,
    val inscriptionsParHeure: Int = 10,
    val ephemereTailleMax: Int = 4 * 1024,
    val intervallePing: Duration = Duration.ofSeconds(30),
    val intervallePurge: Duration = Duration.ofHours(1),
) {
    companion object {
        /** Chemin du fichier : variable `COCOONING_CONFIG`, sinon `config/server.conf`. */
        fun fichierParDefaut(): File = File(System.getenv("COCOONING_CONFIG") ?: "config/server.conf")

        /** Lit [fichier] s'il existe ; les clés absentes gardent leur valeur par défaut. */
        fun charger(fichier: File = fichierParDefaut()): ConfigServeur {
            val brut = if (fichier.isFile) ConfigFactory.parseFile(fichier).resolve() else ConfigFactory.empty()
            if (!brut.hasPath("cocooning")) return ConfigServeur()
            return depuis(brut.getConfig("cocooning"))
        }

        fun depuis(c: Config): ConfigServeur {
            val d = ConfigServeur()
            fun texte(cle: String, defaut: String) = if (c.hasPath(cle)) c.getString(cle) else defaut
            fun entier(cle: String, defaut: Int) = if (c.hasPath(cle)) c.getInt(cle) else defaut
            fun octets(cle: String, defaut: Long) = if (c.hasPath(cle)) c.getBytes(cle) else defaut
            fun reel(cle: String, defaut: Double) = if (c.hasPath(cle)) c.getDouble(cle) else defaut
            fun duree(cle: String, defaut: Duration) = if (c.hasPath(cle)) c.getDuration(cle) else defaut
            fun oui(cle: String, defaut: Boolean) = if (c.hasPath(cle)) c.getBoolean(cle) else defaut
            return ConfigServeur(
                hote = texte("ecoute.hote", d.hote),
                port = entier("ecoute.port", d.port),
                derriereProxy = oui("ecoute.derriereProxy", d.derriereProxy),
                cheminBase = texte("stockage.base", d.cheminBase),
                dossierMedias = texte("stockage.medias", d.dossierMedias),
                membresMax = entier("espace.membresMax", d.membresMax),
                validiteInvitation = duree("espace.validiteInvitation", d.validiteInvitation),
                validiteJeton = duree("auth.validiteJeton", d.validiteJeton),
                validiteDefi = duree("auth.validiteDefi", d.validiteDefi),
                conservationMessages = duree("messages.conservation", d.conservationMessages),
                tailleMaxEnveloppe = octets("messages.tailleMaxEnveloppe", d.tailleMaxEnveloppe.toLong()).toInt(),
                destinatairesMax = entier("messages.destinatairesMax", d.destinatairesMax),
                releveMax = entier("messages.releveMax", d.releveMax),
                preClesMaxParAppareil = entier("cles.preClesMaxParAppareil", d.preClesMaxParAppareil),
                tailleMaxMedia = octets("medias.tailleMax", d.tailleMaxMedia),
                tailleMorceau = octets("medias.tailleMorceau", d.tailleMorceau.toLong()).toInt(),
                quotaMedias = octets("medias.quota", d.quotaMedias),
                seuilAlerteMedias = reel("medias.seuilAlerte", d.seuilAlerteMedias),
                seuilBlocageMedias = reel("medias.seuilBlocage", d.seuilBlocageMedias),
                conservationMediasApresReception = duree("medias.conservationApresReception", d.conservationMediasApresReception),
                conservationMediasMax = duree("medias.conservationMax", d.conservationMediasMax),
                abandonEnvoiMedia = duree("medias.abandonEnvoi", d.abandonEnvoiMedia),
                ntfyUrl = texte("reveil.ntfy.url", d.ntfyUrl),
                ntfyJeton = texte("reveil.ntfy.jeton", d.ntfyJeton),
                requetesParMinute = entier("limites.requetesParMinute", d.requetesParMinute),
                inscriptionsParHeure = entier("limites.inscriptionsParHeure", d.inscriptionsParHeure),
                ephemereTailleMax = octets("tempsReel.ephemereTailleMax", d.ephemereTailleMax.toLong()).toInt(),
                intervallePing = duree("tempsReel.intervallePing", d.intervallePing),
                intervallePurge = duree("purge.intervalle", d.intervallePurge),
            ).also { it.verifier() }
        }
    }

    fun verifier() {
        require(port in 1..65535) { "Port invalide : $port" }
        require(membresMax >= 2) { "Un espace a au moins deux membres" }
        require(tailleMorceau in 64 * 1024..32 * 1024 * 1024) { "Taille de morceau hors de 64 Kio à 32 Mio" }
        require(seuilAlerteMedias in 0.0..1.0 && seuilBlocageMedias in 0.0..1.0) { "Seuils entre 0 et 1" }
        require(!validiteJeton.isNegative && !validiteJeton.isZero) { "Validité du jeton nulle" }
    }
}
