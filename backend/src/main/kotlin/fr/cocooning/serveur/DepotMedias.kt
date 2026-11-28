package fr.cocooning.serveur

import fr.cocooning.protocole.api.EtatMedia
import fr.cocooning.protocole.api.MediaCree
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.util.UUID

/**
 * Médias chiffrés sur le téléphone (ENF-SEC-03) : le serveur ne voit que des blocs d'octets,
 * envoyés par morceaux avec reprise (section 10), rangés sous un identifiant aléatoire.
 * Purge 30 jours après réception par tous les destinataires (ENF-RGPD-04).
 */
class DepotMedias(private val base: Base, private val config: ConfigServeur, private val horloge: Clock) {

    private val journal = LoggerFactory.getLogger(DepotMedias::class.java)
    private val dossier = File(config.dossierMedias).apply { mkdirs() }

    private class Media(
        val id: String,
        val espace: String,
        val auteur: String,
        val taille: Long,
        val tailleMorceau: Int,
        val termine: Boolean,
    ) {
        val nombreMorceaux get() = ((taille + tailleMorceau - 1) / tailleMorceau).toInt()
        fun tailleDu(numero: Int): Int =
            if (numero < nombreMorceaux - 1) tailleMorceau else (taille - tailleMorceau.toLong() * (nombreMorceaux - 1)).toInt()
    }

    /** Destinataires : tous les autres appareils actifs de l'espace au moment de l'envoi. */
    suspend fun creer(appelant: Appelant, taille: Long): MediaCree {
        if (taille !in 1..config.tailleMaxMedia) {
            throw ErreurServeur(HttpStatusCode.PayloadTooLarge, "media_trop_gros", "Média de 1 octet à ${config.tailleMaxMedia} octets")
        }
        val occupe = occupe()
        if (occupe + taille > config.quotaMedias * config.seuilBlocageMedias) {
            journal.warn("Stockage des médias presque plein : {} octets sur {}", occupe, config.quotaMedias)
            throw ErreurServeur(HttpStatusCode.InsufficientStorage, "stockage_plein", "Le serveur n'a plus de place pour les médias")
        }
        val id = UUID.randomUUID().toString()
        base.tx {
            executer(
                "INSERT INTO medias(id, espace, auteur, taille, taille_morceau, termine, cree_le) VALUES (?, ?, ?, ?, ?, 0, ?)",
                id, appelant.espace, appelant.appareil, taille, config.tailleMorceau, horloge.millis(),
            )
            lister(
                """SELECT a.id FROM appareils a JOIN membres m ON m.id = a.membre
                   WHERE m.espace = ? AND a.actif = 1 AND a.id <> ?""",
                appelant.espace, appelant.appareil,
            ) { it.getString(1) }.forEach { executer("INSERT INTO medias_destinataires(media, appareil) VALUES (?, ?)", id, it) }
        }
        File(dossier, id).mkdirs()
        val media = Media(id, appelant.espace, appelant.appareil, taille, config.tailleMorceau, false)
        return MediaCree(id, media.tailleMorceau, media.nombreMorceaux)
    }

    /** Taille attendue du morceau, après vérification que l'appelant peut l'envoyer. */
    suspend fun tailleAttendue(appelant: Appelant, id: String, numero: Int): Int {
        val media = media(id, appelant)
        if (media.auteur != appelant.appareil) throw interdit()
        if (media.termine) throw ErreurServeur(HttpStatusCode.Conflict, "media_termine", "Média déjà terminé")
        if (numero !in 0 until media.nombreMorceaux) throw ErreurServeur(HttpStatusCode.BadRequest, "morceau_invalide", "Morceau hors limites")
        return media.tailleDu(numero)
    }

    /** Range un morceau ; le renvoyer (reprise après coupure) le remplace. */
    suspend fun ecrireMorceau(appelant: Appelant, id: String, numero: Int, octets: ByteArray): EtatMedia {
        val attendu = tailleAttendue(appelant, id, numero)
        if (octets.size != attendu) {
            throw ErreurServeur(HttpStatusCode.BadRequest, "taille_morceau", "Morceau de $attendu octets attendu")
        }
        val provisoire = File(File(dossier, id), "$numero.part")
        provisoire.writeBytes(octets)
        Files.move(provisoire.toPath(), File(File(dossier, id), "$numero").toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        base.tx { executer("INSERT OR IGNORE INTO medias_morceaux(media, numero) VALUES (?, ?)", id, numero) }
        return etat(appelant, id)
    }

    suspend fun terminer(appelant: Appelant, id: String): EtatMedia {
        val media = media(id, appelant)
        if (media.auteur != appelant.appareil) throw interdit()
        val recus = morceaux(id)
        if (recus.size != media.nombreMorceaux) {
            throw ErreurServeur(HttpStatusCode.Conflict, "media_incomplet", "Il manque ${media.nombreMorceaux - recus.size} morceaux")
        }
        base.tx { executer("UPDATE medias SET termine = 1 WHERE id = ?", id) }
        return etat(appelant, id)
    }

    suspend fun etat(appelant: Appelant, id: String): EtatMedia {
        val media = media(id, appelant)
        return EtatMedia(media.id, media.taille, media.tailleMorceau, morceaux(id), media.termine)
    }

    /** Fichier d'un morceau d'un média terminé, pour un appareil de l'espace. */
    suspend fun fichierMorceau(appelant: Appelant, id: String, numero: Int): File {
        val media = media(id, appelant)
        if (!media.termine) throw ErreurServeur(HttpStatusCode.Conflict, "media_incomplet", "Média pas encore terminé")
        if (numero !in 0 until media.nombreMorceaux) throw ErreurServeur(HttpStatusCode.NotFound, "morceau_invalide", "Morceau inconnu")
        return File(File(dossier, id), "$numero")
    }

    /** Le destinataire a tout téléchargé : le compte à rebours de la purge peut commencer. */
    suspend fun marquerRecu(appelant: Appelant, id: String) {
        media(id, appelant)
        base.tx {
            executer(
                "UPDATE medias_destinataires SET recu_le = ? WHERE media = ? AND appareil = ? AND recu_le IS NULL",
                horloge.millis(), id, appelant.appareil,
            )
        }
    }

    /** Octets occupés par les médias encore présents. */
    suspend fun occupe(): Long = base.tx { premier("SELECT COALESCE(SUM(taille), 0) FROM medias") { it.getLong(1) }!! }

    /**
     * Supprime les médias reçus par tous depuis plus de 30 jours, ceux qui ont dépassé la durée
     * maximale, et les envois abandonnés. Prévient l'administrateur au-delà de 80 % du quota.
     */
    suspend fun purger(): Int {
        val maintenant = horloge.millis()
        val aSupprimer = base.tx {
            lister(
                """SELECT m.id FROM medias m WHERE
                     (m.termine = 1 AND NOT EXISTS (SELECT 1 FROM medias_destinataires d WHERE d.media = m.id AND d.recu_le IS NULL)
                        AND COALESCE((SELECT MAX(recu_le) FROM medias_destinataires d WHERE d.media = m.id), m.cree_le) < ?)
                     OR m.cree_le < ?
                     OR (m.termine = 0 AND m.cree_le < ?)""",
                maintenant - config.conservationMediasApresReception.toMillis(),
                maintenant - config.conservationMediasMax.toMillis(),
                maintenant - config.abandonEnvoiMedia.toMillis(),
            ) { it.getString(1) }
        }
        aSupprimer.forEach { id ->
            base.tx { executer("DELETE FROM medias WHERE id = ?", id) }
            File(dossier, id).deleteRecursively()
        }
        val occupe = occupe()
        if (occupe > config.quotaMedias * config.seuilAlerteMedias) {
            journal.warn("ALERTE : médias à {} % du quota", occupe * 100 / config.quotaMedias)
        }
        return aSupprimer.size
    }

    private suspend fun media(id: String, appelant: Appelant): Media {
        val media = base.tx {
            premier("SELECT id, espace, auteur, taille, taille_morceau, termine FROM medias WHERE id = ?", id) {
                Media(it.getString(1), it.getString(2), it.getString(3), it.getLong(4), it.getInt(5), it.getInt(6) == 1)
            }
        }
        // Un média d'un autre espace est traité comme inexistant.
        if (media == null || media.espace != appelant.espace) {
            throw ErreurServeur(HttpStatusCode.NotFound, "media_inconnu", "Média inconnu")
        }
        return media
    }

    private suspend fun morceaux(id: String): List<Int> =
        base.tx { lister("SELECT numero FROM medias_morceaux WHERE media = ? ORDER BY numero", id) { it.getInt(1) } }

    private fun interdit() = ErreurServeur(HttpStatusCode.Forbidden, "interdit", "Seul l'auteur envoie ce média")
}
