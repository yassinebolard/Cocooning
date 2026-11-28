package fr.cocooning.serveur

import fr.cocooning.protocole.api.EtatCles
import fr.cocooning.protocole.api.PaquetApi
import fr.cocooning.protocole.api.PaquetsMembre
import fr.cocooning.protocole.api.PreCleApi
import fr.cocooning.protocole.api.PublicationClesApi
import io.ktor.http.HttpStatusCode

/**
 * Clés publiques Signal des appareils (ADR 0003, section 2). Le serveur les range et les
 * distribue sans pouvoir rien en faire : il n'a aucune clé privée.
 */
class DepotCles(private val base: Base, private val config: ConfigServeur) {

    /**
     * Publication par l'appareil. L'identité est figée à la première publication : un téléphone
     * qui change d'identité doit passer par un remplacement d'appareil, visible de l'autre membre.
     */
    suspend fun publier(appelant: Appelant, publication: PublicationClesApi): EtatCles {
        verifier(publication)
        return base.tx {
            val identite = premier("SELECT identite FROM cles WHERE appareil = ?", appelant.appareil) { it.getBytes(1) }
            if (identite != null && !identite.contentEquals(publication.identite)) {
                throw ErreurServeur(HttpStatusCode.Conflict, "identite_figee", "L'identité de cet appareil ne change pas")
            }
            val restantes = premier("SELECT COUNT(*) FROM pre_cles WHERE appareil = ?", appelant.appareil) { it.getInt(1) }!!
            if (restantes + publication.preClesUniques.size > config.preClesMaxParAppareil) {
                throw ErreurServeur(HttpStatusCode.BadRequest, "trop_de_pre_cles", "Au plus ${config.preClesMaxParAppareil} pré-clés")
            }
            executer(
                """INSERT INTO cles(appareil, id_enregistrement, identite, signee_id, signee_cle, signee_signature,
                       kyber_id, kyber_cle, kyber_signature) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT(appareil) DO UPDATE SET id_enregistrement = excluded.id_enregistrement,
                       signee_id = excluded.signee_id, signee_cle = excluded.signee_cle,
                       signee_signature = excluded.signee_signature, kyber_id = excluded.kyber_id,
                       kyber_cle = excluded.kyber_cle, kyber_signature = excluded.kyber_signature""",
                appelant.appareil, publication.idEnregistrement, publication.identite,
                publication.preCleSignee.id, publication.preCleSignee.cle, publication.preCleSignee.signature,
                publication.preCleKyber.id, publication.preCleKyber.cle, publication.preCleKyber.signature,
            )
            publication.preClesUniques.forEach {
                executer("INSERT OR REPLACE INTO pre_cles(appareil, id, cle) VALUES (?, ?, ?)", appelant.appareil, it.id, it.cle)
            }
            EtatCles(premier("SELECT COUNT(*) FROM pre_cles WHERE appareil = ?", appelant.appareil) { it.getInt(1) }!!)
        }
    }

    suspend fun etat(appelant: Appelant): EtatCles = base.tx {
        EtatCles(premier("SELECT COUNT(*) FROM pre_cles WHERE appareil = ?", appelant.appareil) { it.getInt(1) }!!)
    }

    /**
     * Paquets pour écrire aux appareils actifs de [membre], dans l'espace de l'appelant. Chaque
     * paquet emporte une pré-clé unique, effacée aussitôt : elle ne sera jamais redonnée.
     */
    suspend fun paquets(appelant: Appelant, membre: String): PaquetsMembre = base.tx {
        val memeEspace = premier("SELECT 1 FROM membres WHERE id = ? AND espace = ?", membre, appelant.espace) { true }
        if (memeEspace == null) throw ErreurServeur(HttpStatusCode.NotFound, "membre_inconnu", "Membre inconnu")
        val lignes = lister(
            """SELECT a.id, a.numero, c.id_enregistrement, c.identite, c.signee_id, c.signee_cle, c.signee_signature,
                      c.kyber_id, c.kyber_cle, c.kyber_signature
               FROM appareils a JOIN cles c ON c.appareil = a.id
               WHERE a.membre = ? AND a.actif = 1 ORDER BY a.numero""",
            membre,
        ) {
            PaquetApi(
                appareil = it.getString(1),
                numero = it.getInt(2),
                idEnregistrement = it.getInt(3),
                identite = it.getBytes(4),
                preCleSignee = PreCleApi(it.getInt(5), it.getBytes(6), it.getBytes(7)),
                preCleKyber = PreCleApi(it.getInt(8), it.getBytes(9), it.getBytes(10)),
            )
        }
        PaquetsMembre(
            membre,
            lignes.map { paquet ->
                val unique = premier(
                    "SELECT id, cle FROM pre_cles WHERE appareil = ? ORDER BY id LIMIT 1", paquet.appareil,
                ) { PreCleApi(it.getInt(1), it.getBytes(2)) }
                unique?.let { executer("DELETE FROM pre_cles WHERE appareil = ? AND id = ?", paquet.appareil, it.id) }
                PaquetApi(
                    paquet.appareil, paquet.numero, paquet.idEnregistrement, paquet.identite,
                    paquet.preCleSignee, paquet.preCleKyber, unique,
                )
            },
        )
    }

    /** Bornes de taille seulement : le serveur ne sait pas vérifier les signatures Signal (XEdDSA). */
    private fun verifier(p: PublicationClesApi) {
        fun refus(raison: String): Nothing = throw ErreurServeur(HttpStatusCode.BadRequest, "cles_invalides", raison)
        if (p.identite.size !in 32..64) refus("Identité de taille inattendue")
        listOf(p.preCleSignee, p.preCleKyber).forEach {
            if (it.signature == null || it.signature!!.size !in 32..128) refus("Pré-clé sans signature valable")
        }
        if (p.preCleSignee.cle.size !in 32..64) refus("Pré-clé signée de taille inattendue")
        if (p.preCleKyber.cle.size !in 800..2048) refus("Pré-clé Kyber de taille inattendue")
        if (p.preClesUniques.map { it.id }.toSet().size != p.preClesUniques.size) refus("Identifiants de pré-clés en double")
        if (p.preClesUniques.any { it.cle.size !in 32..64 }) refus("Pré-clé unique de taille inattendue")
    }
}
