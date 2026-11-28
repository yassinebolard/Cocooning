package fr.cocooning.core.reseau

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Inscription de ce téléphone auprès du serveur.
 *
 * @param secretRecuperation 32 octets aléatoires ; le serveur n'en connaît que le SHA-256. Rangé
 *   dans les préférences chiffrées, il part avec la sauvegarde (EF-CPT-08) et permet à un
 *   nouveau téléphone de reprendre la place de celui-ci (EF-CPT-09).
 */
class Compte(
    val espace: String,
    val membre: String,
    val appareil: String,
    val numero: Int,
    val secretRecuperation: ByteArray,
)

interface MagasinCompte {
    suspend fun lire(): Compte?
    suspend fun ecrire(compte: Compte)

    /** Appareils déjà vus pour chaque membre (identifiants serveur), pour repérer un téléphone remplacé. */
    suspend fun appareilsConnus(): Map<String, Set<String>>
    suspend fun ecrireAppareilsConnus(appareils: Map<String, Set<String>>)
}

class MagasinCompteMemoire(private var compte: Compte? = null) : MagasinCompte {
    private var connus: Map<String, Set<String>> = emptyMap()
    override suspend fun appareilsConnus() = connus
    override suspend fun ecrireAppareilsConnus(appareils: Map<String, Set<String>>) {
        connus = appareils
    }
    override suspend fun lire() = compte
    override suspend fun ecrire(compte: Compte) {
        this.compte = compte
    }
}

/** Compte rangé dans les préférences chiffrées de `:core:securite` (ENF-SEC-04). */
class MagasinCompteChiffre(private val preferences: DataStore<Preferences>) : MagasinCompte {

    override suspend fun lire(): Compte? {
        val p = preferences.data.first()
        return Compte(
            espace = p[ESPACE] ?: return null,
            membre = p[MEMBRE] ?: return null,
            appareil = p[APPAREIL] ?: return null,
            numero = p[NUMERO] ?: return null,
            secretRecuperation = p[SECRET] ?: return null,
        )
    }

    override suspend fun ecrire(compte: Compte) {
        preferences.edit {
            it[ESPACE] = compte.espace
            it[MEMBRE] = compte.membre
            it[APPAREIL] = compte.appareil
            it[NUMERO] = compte.numero
            it[SECRET] = compte.secretRecuperation
        }
    }

    override suspend fun appareilsConnus(): Map<String, Set<String>> =
        preferences.data.first()[CONNUS].orEmpty().split(';').filter { it.contains('=') }.associate { ligne ->
            val (membre, liste) = ligne.split('=', limit = 2)
            membre to liste.split(',').filter(String::isNotBlank).toSet()
        }

    override suspend fun ecrireAppareilsConnus(appareils: Map<String, Set<String>>) {
        preferences.edit { p ->
            p[CONNUS] = appareils.entries.joinToString(";") { (membre, liste) -> "$membre=${liste.joinToString(",")}" }
        }
    }

    private companion object {
        val CONNUS = stringPreferencesKey("compte_appareils_connus")
        val ESPACE = stringPreferencesKey("compte_espace")
        val MEMBRE = stringPreferencesKey("compte_membre")
        val APPAREIL = stringPreferencesKey("compte_appareil")
        val NUMERO = intPreferencesKey("compte_numero")
        val SECRET = byteArrayPreferencesKey("compte_secret_recuperation")
    }
}
