package fr.cocooning.serveur

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Base SQLite du serveur (cahier section 9.3 : SQLite en mode WAL, pas de serveur de base).
 * Une seule connexion, accès sérialisés : largement assez pour un couple ou une famille.
 *
 * Elle ne contient que des métadonnées (qui, quand, quelle taille) et des octets chiffrés par
 * les téléphones : aucun contenu de message ni clé privée.
 */
class Base private constructor(private val connexion: Connection) : AutoCloseable {

    companion object {
        /** Base sur disque, créée si besoin. */
        fun ouvrir(chemin: String): Base {
            File(chemin).absoluteFile.parentFile?.mkdirs()
            return Base(DriverManager.getConnection("jdbc:sqlite:$chemin")).also { it.preparer(enMemoire = false) }
        }

        /** Base en mémoire, pour les tests. */
        fun enMemoire(): Base = Base(DriverManager.getConnection("jdbc:sqlite::memory:")).also { it.preparer(enMemoire = true) }
    }

    private fun preparer(enMemoire: Boolean) {
        connexion.createStatement().use { s ->
            if (!enMemoire) s.execute("PRAGMA journal_mode=WAL")
            s.execute("PRAGMA foreign_keys=ON")
            s.execute("PRAGMA busy_timeout=5000")
            SCHEMA.forEach(s::execute)
        }
    }

    /** Exécute [bloc] dans une transaction, annulée à la moindre exception. */
    @Synchronized
    fun <R> transaction(bloc: Connection.() -> R): R {
        connexion.autoCommit = false
        return try {
            connexion.bloc().also { connexion.commit() }
        } catch (e: Throwable) {
            connexion.rollback()
            throw e
        } finally {
            connexion.autoCommit = true
        }
    }

    /** [transaction] hors du fil d'appel, pour les routes Ktor. */
    suspend fun <R> tx(bloc: Connection.() -> R): R = withContext(Dispatchers.IO) { transaction(bloc) }

    override fun close() = connexion.close()
}

private val SCHEMA = listOf(
    """CREATE TABLE IF NOT EXISTS espaces (id TEXT PRIMARY KEY, cree_le INTEGER NOT NULL)""",
    """CREATE TABLE IF NOT EXISTS membres (
        id TEXT PRIMARY KEY,
        espace TEXT NOT NULL REFERENCES espaces(id),
        empreinte_recuperation BLOB NOT NULL,
        cree_le INTEGER NOT NULL)""",
    """CREATE TABLE IF NOT EXISTS appareils (
        id TEXT PRIMARY KEY,
        membre TEXT NOT NULL REFERENCES membres(id),
        numero INTEGER NOT NULL,
        cle_auth BLOB NOT NULL,
        actif INTEGER NOT NULL,
        reveil_type TEXT NOT NULL DEFAULT 'Aucun',
        reveil_cible TEXT NOT NULL DEFAULT '',
        cree_le INTEGER NOT NULL)""",
    """CREATE INDEX IF NOT EXISTS appareils_membre ON appareils(membre, actif)""",
    """CREATE TABLE IF NOT EXISTS invitations (
        code TEXT PRIMARY KEY,
        espace TEXT NOT NULL REFERENCES espaces(id),
        expire_le INTEGER NOT NULL)""",
    """CREATE TABLE IF NOT EXISTS cles (
        appareil TEXT PRIMARY KEY REFERENCES appareils(id) ON DELETE CASCADE,
        id_enregistrement INTEGER NOT NULL,
        identite BLOB NOT NULL,
        signee_id INTEGER NOT NULL, signee_cle BLOB NOT NULL, signee_signature BLOB NOT NULL,
        kyber_id INTEGER NOT NULL, kyber_cle BLOB NOT NULL, kyber_signature BLOB NOT NULL)""",
    """CREATE TABLE IF NOT EXISTS pre_cles (
        appareil TEXT NOT NULL REFERENCES appareils(id) ON DELETE CASCADE,
        id INTEGER NOT NULL,
        cle BLOB NOT NULL,
        PRIMARY KEY (appareil, id))""",
    """CREATE TABLE IF NOT EXISTS boites (
        seq INTEGER PRIMARY KEY AUTOINCREMENT,
        destinataire TEXT NOT NULL REFERENCES appareils(id) ON DELETE CASCADE,
        id_message TEXT NOT NULL,
        expediteur TEXT NOT NULL,
        type TEXT NOT NULL,
        contenu BLOB NOT NULL,
        depose_le INTEGER NOT NULL)""",
    """CREATE INDEX IF NOT EXISTS boites_destinataire ON boites(destinataire, seq)""",
    """CREATE INDEX IF NOT EXISTS boites_depot ON boites(depose_le)""",
    """CREATE TABLE IF NOT EXISTS depots (
        expediteur TEXT NOT NULL,
        id_message TEXT NOT NULL,
        depose_le INTEGER NOT NULL,
        PRIMARY KEY (expediteur, id_message))""",
    """CREATE TABLE IF NOT EXISTS medias (
        id TEXT PRIMARY KEY,
        espace TEXT NOT NULL,
        auteur TEXT NOT NULL,
        taille INTEGER NOT NULL,
        taille_morceau INTEGER NOT NULL,
        termine INTEGER NOT NULL,
        cree_le INTEGER NOT NULL)""",
    """CREATE TABLE IF NOT EXISTS medias_morceaux (
        media TEXT NOT NULL REFERENCES medias(id) ON DELETE CASCADE,
        numero INTEGER NOT NULL,
        PRIMARY KEY (media, numero))""",
    """CREATE TABLE IF NOT EXISTS medias_destinataires (
        media TEXT NOT NULL REFERENCES medias(id) ON DELETE CASCADE,
        appareil TEXT NOT NULL,
        recu_le INTEGER,
        PRIMARY KEY (media, appareil))""",
)

// Petits outils JDBC

internal fun Connection.executer(sql: String, vararg parametres: Any?): Int =
    prepareStatement(sql).use { it.lier(parametres).executeUpdate() }

internal fun <T> Connection.lister(sql: String, vararg parametres: Any?, ligne: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { s ->
        s.lier(parametres).executeQuery().use { rs -> buildList { while (rs.next()) add(ligne(rs)) } }
    }

internal fun <T> Connection.premier(sql: String, vararg parametres: Any?, ligne: (ResultSet) -> T): T? =
    lister(sql, *parametres, ligne = ligne).firstOrNull()

private fun PreparedStatement.lier(parametres: Array<out Any?>): PreparedStatement {
    parametres.forEachIndexed { i, p ->
        when (p) {
            null -> setObject(i + 1, null)
            is String -> setString(i + 1, p)
            is Int -> setInt(i + 1, p)
            is Long -> setLong(i + 1, p)
            is Boolean -> setInt(i + 1, if (p) 1 else 0)
            is ByteArray -> setBytes(i + 1, p)
            else -> error("Type de paramètre non géré : ${p::class}")
        }
    }
    return this
}
