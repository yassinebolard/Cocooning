package fr.cocooning.feature.sauvegarde

import androidx.navigation3.runtime.NavKey
import fr.cocooning.core.securite.sauvegarde.ContenuSauvegarde
import fr.cocooning.core.securite.sauvegarde.SauvegardeCocooning
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.LocalDate

/** Création de la phrase de récupération et export du fichier, au-dessus de l'accueil. */
@Serializable
data object SauvegardeCle : NavKey

/** Ce qui chiffre, lit et remet en place une sauvegarde. Fourni par [SauvegardeCocooning] dans l'appli. */
interface CoffreSauvegarde {
    /** Produit le fichier chiffré par [phrase]. Long (Argon2id, quelques secondes). */
    suspend fun exporter(phrase: String): ByteArray

    /** Vérifie la phrase sans rien modifier. Lève [fr.cocooning.core.securite.sauvegarde.EchecOuverture]. */
    suspend fun ouvrir(fichier: ByteArray, phrase: String): ContenuSauvegarde

    /** Remet en place les bases et les préférences ; l'appli doit redémarrer ensuite. */
    suspend fun restaurer(contenu: ContenuSauvegarde)
}

/** Adaptateur du moteur de `:core:securite`. */
class CoffreCocooning(private val moteur: SauvegardeCocooning) : CoffreSauvegarde {
    override suspend fun exporter(phrase: String): ByteArray = moteur.exporter(phrase)
    override suspend fun ouvrir(fichier: ByteArray, phrase: String): ContenuSauvegarde = moteur.lire(fichier, phrase)
    override suspend fun restaurer(contenu: ContenuSauvegarde) = moteur.restaurer(contenu)
}

/**
 * Lecture et écriture d'un fichier choisi par l'utilisateur dans le sélecteur d'Android. Les
 * adresses sont des `content://` sous forme de texte, pour tester sans Android.
 */
interface FichiersSauvegarde {
    suspend fun ecrire(adresse: String, octets: ByteArray)

    /** Lève [FichierTropGros] au-delà de [FichiersAndroid.TAILLE_MAX]. */
    suspend fun lire(adresse: String): ByteArray

    /** Nom affiché du fichier, s'il est connu. */
    suspend fun nom(adresse: String): String?
}

class FichierTropGros : java.io.IOException("Fichier trop gros pour une sauvegarde Cocooning")

/** Ce que l'appli retient de la sauvegarde, dans les préférences chiffrées. */
interface SuiviSauvegarde {
    val dernierExport: Flow<LocalDate?>
    val rappelReporteJusquau: Flow<LocalDate?>

    /** Vrai une fois l'accueil du premier lancement passé, ou si le téléphone a déjà des données. */
    val premierLancementTermine: Flow<Boolean>

    suspend fun noterExport(jour: LocalDate)
    suspend fun reporterRappel(jusquau: LocalDate)
    suspend fun terminerPremierLancement()
}

class DependancesSauvegarde(
    val coffre: CoffreSauvegarde,
    val fichiers: FichiersSauvegarde,
    val suivi: SuiviSauvegarde,
    val horloge: Clock = Clock.systemDefaultZone(),
)
