package fr.cocooning.serveur

import fr.cocooning.protocole.api.AccuseDepot
import fr.cocooning.protocole.api.AppareilsIncoherents
import fr.cocooning.protocole.api.EnvoiMessage
import fr.cocooning.protocole.api.MessageRecu
import fr.cocooning.protocole.api.Releve
import fr.cocooning.protocole.api.TypeEnveloppeApi
import io.ktor.http.HttpStatusCode
import java.time.Clock

/**
 * Boîtes aux lettres chiffrées, une par appareil (ADR 0003, section 7). Le serveur range des
 * octets qu'il ne sait pas lire, numérotés dans l'ordre d'arrivée, et les efface dès que le
 * téléphone les a acquittés, ou au bout de 30 jours.
 */
class DepotMessages(private val base: Base, private val config: ConfigServeur, private val horloge: Clock) {

    /**
     * Dépose un message chiffré pour chaque appareil visé.
     *
     * @return l'accusé et les appareils servis, à réveiller. Un renvoi du même identifiant rend
     *   l'accusé d'origine sans rien déposer.
     */
    suspend fun deposer(appelant: Appelant, envoi: EnvoiMessage): Pair<AccuseDepot, List<String>> {
        if (envoi.id.isBlank() || envoi.id.length > 64) {
            throw ErreurServeur(HttpStatusCode.BadRequest, "id_invalide", "Identifiant de message invalide")
        }
        if (envoi.envois.isEmpty() || envoi.envois.size > config.destinatairesMax) {
            throw ErreurServeur(HttpStatusCode.BadRequest, "destinataires_invalides", "Entre 1 et ${config.destinatairesMax} appareils")
        }
        if (envoi.envois.any { it.contenu.isEmpty() || it.contenu.size > config.tailleMaxEnveloppe }) {
            throw ErreurServeur(HttpStatusCode.PayloadTooLarge, "trop_gros", "Message chiffré vide ou trop gros")
        }
        return base.tx {
            premier(
                "SELECT depose_le FROM depots WHERE expediteur = ? AND id_message = ?", appelant.appareil, envoi.id,
            ) { it.getLong(1) }?.let { return@tx AccuseDepot(envoi.id, it) to emptyList() }

            // Appareils actifs de l'espace, par membre.
            val actifs = lister(
                """SELECT a.id, a.membre FROM appareils a JOIN membres m ON m.id = a.membre
                   WHERE m.espace = ? AND a.actif = 1""",
                appelant.espace,
            ) { it.getString(1) to it.getString(2) }.toMap()

            val vises = envoi.envois.map { it.appareil }
            if (vises.toSet().size != vises.size) {
                throw ErreurServeur(HttpStatusCode.BadRequest, "destinataires_invalides", "Appareil visé deux fois")
            }
            val enTrop = vises.filter { it !in actifs || it == appelant.appareil }
            val membresVises = vises.mapNotNull { actifs[it] }.toSet()
            val manquants = actifs.filter { (id, membre) -> membre in membresVises && id !in vises && id != appelant.appareil }.keys
            if (enTrop.isNotEmpty() || manquants.isNotEmpty()) {
                throw ErreurServeur(
                    HttpStatusCode.Conflict, "appareils_incoherents", "Liste d'appareils à mettre à jour",
                    AppareilsIncoherents(manquants.sorted(), enTrop.sorted()),
                )
            }

            val maintenant = horloge.millis()
            envoi.envois.forEach {
                executer(
                    """INSERT INTO boites(destinataire, id_message, expediteur, type, contenu, depose_le)
                       VALUES (?, ?, ?, ?, ?, ?)""",
                    it.appareil, envoi.id, appelant.appareil, it.type.name, it.contenu, maintenant,
                )
            }
            executer("INSERT INTO depots(expediteur, id_message, depose_le) VALUES (?, ?, ?)", appelant.appareil, envoi.id, maintenant)
            AccuseDepot(envoi.id, maintenant) to vises
        }
    }

    /** Messages en attente pour l'appareil, après le numéro [apres], dans l'ordre d'arrivée. */
    suspend fun relever(appelant: Appelant, apres: Long, limite: Int): Releve {
        val n = limite.coerceIn(1, config.releveMax)
        return base.tx {
            val lignes = lister(
                """SELECT b.seq, b.id_message, a.membre, b.expediteur, a.numero, b.type, b.contenu, b.depose_le
                   FROM boites b JOIN appareils a ON a.id = b.expediteur
                   WHERE b.destinataire = ? AND b.seq > ? ORDER BY b.seq LIMIT ?""",
                appelant.appareil, apres, n + 1,
            ) {
                MessageRecu(
                    seq = it.getLong(1),
                    id = it.getString(2),
                    expediteurMembre = it.getString(3),
                    expediteurAppareil = it.getString(4),
                    expediteurNumero = it.getInt(5),
                    type = TypeEnveloppeApi.valueOf(it.getString(6)),
                    contenu = it.getBytes(7),
                    deposeLe = it.getLong(8),
                )
            }
            Releve(lignes.take(n), suite = lignes.size > n)
        }
    }

    /** Efface les messages acquittés ; ceux d'un autre appareil ne sont jamais touchés. */
    suspend fun acquitter(appelant: Appelant, seqs: List<Long>): Int {
        if (seqs.size > config.releveMax * 10) {
            throw ErreurServeur(HttpStatusCode.BadRequest, "trop_d_acquittements", "Trop de messages d'un coup")
        }
        return base.tx {
            seqs.distinct().sumOf { executer("DELETE FROM boites WHERE destinataire = ? AND seq = ?", appelant.appareil, it) }
        }
    }

    /** Messages jamais relevés après la durée de conservation (ENF-RGPD-04), et traces d'envoi. */
    suspend fun purger(): Int {
        val limite = horloge.millis() - config.conservationMessages.toMillis()
        return base.tx {
            executer("DELETE FROM depots WHERE depose_le < ?", limite)
            executer("DELETE FROM boites WHERE depose_le < ?", limite)
        }
    }
}
