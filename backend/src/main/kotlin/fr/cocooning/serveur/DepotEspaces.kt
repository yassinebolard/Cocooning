package fr.cocooning.serveur

import fr.cocooning.protocole.api.AppareilApi
import fr.cocooning.protocole.api.EspaceApi
import fr.cocooning.protocole.api.Inscription
import fr.cocooning.protocole.api.Invitation
import fr.cocooning.protocole.api.MembreApi
import fr.cocooning.protocole.api.ReveilApi
import fr.cocooning.protocole.api.TypeReveil
import io.ktor.http.HttpStatusCode
import java.sql.Connection
import java.time.Clock
import java.util.UUID

/** Espaces, membres, appareils et invitations (EF-CPT-02, 03, 06, 09 ; ENF-EVO-01). */
class DepotEspaces(private val base: Base, private val config: ConfigServeur, private val horloge: Clock) {

    class AppareilEnregistre(val appelant: Appelant, val cleAuth: ByteArray, val reveil: ReveilApi)

    /** Premier membre : crée l'espace, son appareil et un code d'invitation. */
    suspend fun creerEspace(cleAuth: ByteArray, empreinteRecuperation: ByteArray): Inscription {
        verifierCle(cleAuth, empreinteRecuperation)
        return base.tx {
            val espace = UUID.randomUUID().toString()
            executer("INSERT INTO espaces(id, cree_le) VALUES (?, ?)", espace, horloge.millis())
            val inscription = ajouterMembre(espace, cleAuth, empreinteRecuperation)
            inscription.copy(invitation = creerInvitation(espace))
        }
    }

    /** Arrivée d'un membre par un code d'invitation, qui ne sert qu'une fois. */
    suspend fun rejoindre(code: String, cleAuth: ByteArray, empreinteRecuperation: ByteArray): Inscription {
        verifierCle(cleAuth, empreinteRecuperation)
        val normalise = code.uppercase().filter { !it.isWhitespace() && it != '-' }
        return base.tx {
            val espace = premier(
                "SELECT espace FROM invitations WHERE code = ? AND expire_le >= ?", normalise, horloge.millis(),
            ) { it.getString(1) } ?: throw ErreurServeur(HttpStatusCode.NotFound, "invitation_invalide", "Code inconnu ou expiré")
            val membres = premier("SELECT COUNT(*) FROM membres WHERE espace = ?", espace) { it.getInt(1) }!!
            if (membres >= config.membresMax) {
                throw ErreurServeur(HttpStatusCode.Conflict, "espace_complet", "L'espace a déjà ${config.membresMax} membres")
            }
            executer("DELETE FROM invitations WHERE code = ?", normalise)
            ajouterMembre(espace, cleAuth, empreinteRecuperation)
        }
    }

    /** Nouveau code pour l'espace de [appelant] (V2 : inviter un autre membre). */
    suspend fun inviter(appelant: Appelant): Invitation = base.tx { creerInvitation(appelant.espace) }

    /**
     * Nouveau téléphone d'un membre (EF-CPT-09), prouvé par le secret de récupération de sa
     * sauvegarde. Les anciens appareils sont désactivés et leurs boîtes et clés effacées ; le
     * nouveau reprend le numéro libéré, si bien que l'autre membre voit l'identité changer.
     *
     * @return l'inscription et les appareils désactivés (pour couper leurs jetons et connexions).
     */
    suspend fun remplacer(
        membre: String,
        secret: ByteArray,
        cleAuth: ByteArray,
        nouvelleEmpreinte: ByteArray,
    ): Pair<Inscription, List<String>> {
        verifierCle(cleAuth, nouvelleEmpreinte)
        return base.tx {
            val (espace, empreinte) = premier("SELECT espace, empreinte_recuperation FROM membres WHERE id = ?", membre) {
                it.getString(1) to it.getBytes(2)
            } ?: throw ErreurServeur(HttpStatusCode.Forbidden, "recuperation_refusee", "Récupération refusée")
            if (!java.security.MessageDigest.isEqual(sha256(secret), empreinte)) {
                throw ErreurServeur(HttpStatusCode.Forbidden, "recuperation_refusee", "Récupération refusée")
            }
            val anciens = lister("SELECT id FROM appareils WHERE membre = ? AND actif = 1", membre) { it.getString(1) }
            anciens.forEach { desactiver(it) }
            executer("UPDATE membres SET empreinte_recuperation = ? WHERE id = ?", nouvelleEmpreinte, membre)
            val (appareil, numero) = ajouterAppareil(membre, cleAuth)
            Inscription(espace, membre, appareil, numero) to anciens
        }
    }

    suspend fun appareil(id: String): AppareilEnregistre? = base.tx {
        premier(
            """SELECT a.id, a.membre, m.espace, a.numero, a.cle_auth, a.reveil_type, a.reveil_cible
               FROM appareils a JOIN membres m ON m.id = a.membre WHERE a.id = ? AND a.actif = 1""",
            id,
        ) {
            AppareilEnregistre(
                Appelant(it.getString(1), it.getString(2), it.getString(3), it.getInt(4)),
                it.getBytes(5),
                ReveilApi(TypeReveil.valueOf(it.getString(6)), it.getString(7)),
            )
        }
    }

    suspend fun espace(espace: String): EspaceApi = base.tx { espaceDe(espace) }

    suspend fun changerReveil(appelant: Appelant, reveil: ReveilApi) {
        if (reveil.cible.length > 512) throw ErreurServeur(HttpStatusCode.BadRequest, "reveil_invalide", "Cible trop longue")
        base.tx {
            executer("UPDATE appareils SET reveil_type = ?, reveil_cible = ? WHERE id = ?", reveil.type.name, reveil.cible, appelant.appareil)
        }
    }

    private fun verifierCle(cleAuth: ByteArray, empreinte: ByteArray) {
        Authentification.lireCleAuth(cleAuth)
        if (empreinte.size != 32) {
            throw ErreurServeur(HttpStatusCode.BadRequest, "empreinte_invalide", "Empreinte SHA-256 de 32 octets attendue")
        }
    }

    private fun Connection.ajouterMembre(espace: String, cleAuth: ByteArray, empreinte: ByteArray): Inscription {
        val membre = UUID.randomUUID().toString()
        executer(
            "INSERT INTO membres(id, espace, empreinte_recuperation, cree_le) VALUES (?, ?, ?, ?)",
            membre, espace, empreinte, horloge.millis(),
        )
        val (appareil, numero) = ajouterAppareil(membre, cleAuth)
        return Inscription(espace, membre, appareil, numero)
    }

    /** Le plus petit numéro Signal libre du membre, de 1 à 127. */
    private fun Connection.ajouterAppareil(membre: String, cleAuth: ByteArray): Pair<String, Int> {
        val pris = lister("SELECT numero FROM appareils WHERE membre = ? AND actif = 1", membre) { it.getInt(1) }.toSet()
        val numero = (1..127).firstOrNull { it !in pris }
            ?: throw ErreurServeur(HttpStatusCode.Conflict, "trop_d_appareils", "Trop d'appareils pour ce membre")
        val id = UUID.randomUUID().toString()
        executer(
            "INSERT INTO appareils(id, membre, numero, cle_auth, actif, cree_le) VALUES (?, ?, ?, ?, 1, ?)",
            id, membre, numero, cleAuth, horloge.millis(),
        )
        return id to numero
    }

    private fun Connection.desactiver(appareil: String) {
        executer("UPDATE appareils SET actif = 0, reveil_type = 'Aucun', reveil_cible = '' WHERE id = ?", appareil)
        executer("DELETE FROM boites WHERE destinataire = ?", appareil)
        executer("DELETE FROM pre_cles WHERE appareil = ?", appareil)
        executer("DELETE FROM cles WHERE appareil = ?", appareil)
    }

    private fun Connection.creerInvitation(espace: String): Invitation {
        val code = (1..8).map { ALPHABET[aleatoire.nextInt(ALPHABET.length)] }.joinToString("")
        val expire = horloge.millis() + config.validiteInvitation.toMillis()
        executer("INSERT INTO invitations(code, espace, expire_le) VALUES (?, ?, ?)", code, espace, expire)
        return Invitation(code, expire)
    }

    private fun Connection.espaceDe(espace: String): EspaceApi {
        val appareils = lister(
            """SELECT m.id, a.id, a.numero FROM membres m
               LEFT JOIN appareils a ON a.membre = m.id AND a.actif = 1
               WHERE m.espace = ? ORDER BY m.cree_le, a.numero""",
            espace,
        ) { Triple(it.getString(1), it.getString(2), it.getInt(3)) }
        val membres = appareils.groupBy({ it.first }) { it.second?.let { id -> AppareilApi(id, it.third) } }
            .map { (membre, liste) -> MembreApi(membre, liste.filterNotNull()) }
        return EspaceApi(espace, membres)
    }

    companion object {
        /** Sans I, O, 0 ni 1, qu'on confond à l'écran : 32^8, soit plus de 10^12 codes. */
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
