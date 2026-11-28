package com.agdc.cocooning.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import fr.cocooning.core.cycle.donnees.CycleRepository
import fr.cocooning.core.cycle.donnees.CycleRepositoryLocal
import fr.cocooning.core.cycle.donnees.room.BaseCycle
import fr.cocooning.core.cycle.donnees.room.SourceCyclesRoom
import fr.cocooning.core.securite.PreferencesChiffrees
import fr.cocooning.core.securite.sauvegarde.SauvegardeCocooning
import fr.cocooning.feature.accueil.AccueilRepository
import fr.cocooning.feature.accueil.AccueilRepositoryLocal
import fr.cocooning.feature.cycle.DependancesCycle
import fr.cocooning.feature.sauvegarde.CoffreCocooning
import fr.cocooning.feature.sauvegarde.DependancesSauvegarde
import fr.cocooning.feature.sauvegarde.FichiersAndroid
import fr.cocooning.feature.sauvegarde.SuiviSauvegardePreferences
import com.agdc.cocooning.BuildConfig
import fr.cocooning.core.chiffrement.MoteurSignal
import fr.cocooning.core.chiffrement.room.BaseSignal
import fr.cocooning.core.chiffrement.room.StockageSignalRoom
import fr.cocooning.core.messagerie.domaine.IdMembre
import fr.cocooning.core.messagerie.donnees.MessagerieRepository
import fr.cocooning.core.messagerie.donnees.MessagerieRepositoryLocal
import fr.cocooning.core.messagerie.donnees.room.BaseMessagerie
import fr.cocooning.core.messagerie.donnees.room.SourceMessagesRoom
import fr.cocooning.core.reseau.ApiKtor
import fr.cocooning.core.reseau.CleAppareilKeystore
import fr.cocooning.core.reseau.MagasinCompteChiffre
import fr.cocooning.core.reseau.ServiceMessagerie
import fr.cocooning.core.reseau.ServiceMessagerieReseau
import fr.cocooning.core.reseau.Synchroniseur
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.StateFlow

/**
 * Injection de dépendances à la main : un seul objet, créé par [com.agdc.cocooning.CocooningApplication],
 * qui fournit les dépôts aux ViewModel. Le cycle est stocké sur le téléphone (Room chiffré par SQLCipher) ; le reste
 * est encore fictif. Il sera remplacé par Hilt (ou complété) quand les autres données arriveront.
 */
class ConteneurApp(context: Context) {
    private val appContext = context.applicationContext

    private val baseCycle: BaseCycle by lazy { BaseCycle.creer(appContext) }

    /**
     * Préférences de l'application, chiffrées (ENF-SEC-04). Aucune ancienne SharedPreferences à
     * migrer à ce jour : `osmdroid.xml` appartient à la bibliothèque de carte et reste en clair.
     * Une préférence en clair ajoutée par erreur se migre en ajoutant son nom à la liste.
     */
    val preferences: DataStore<Preferences> by lazy {
        PreferencesChiffrees.ouvrir(appContext, NOM_PREFERENCES, anciennesPreferences = emptyList())
    }

    /**
     * Sauvegarde pour le changement de téléphone (EF-CPT-08). L'accès à la base reste paresseux :
     * au premier lancement, la restauration remplace le fichier avant que Room ne l'ouvre.
     */
    private val sauvegarde: SauvegardeCocooning by lazy {
        SauvegardeCocooning(
            appContext,
            // Jamais BaseSignal : les clés de messagerie ne quittent pas le téléphone (ADR 0003).
            bases = mapOf(
                BaseCycle.NOM to { BaseCycle.pourSauvegarde(baseCycle)() },
                BaseMessagerie.NOM to { BaseMessagerie.pourSauvegarde(baseMessagerie)() },
            ),
            preferences = mapOf(NOM_PREFERENCES to preferences),
        )
    }

    /** Ce dont les écrans de sauvegarde ont besoin. */
    val dependancesSauvegarde: DependancesSauvegarde by lazy {
        DependancesSauvegarde(
            coffre = CoffreCocooning(sauvegarde),
            fichiers = FichiersAndroid(appContext),
            suivi = SuiviSauvegardePreferences(
                preferences,
                // Une installation mise à jour qui a déjà des données ne revoit pas l'accueil.
                donneesDejaPresentes = { appContext.getDatabasePath(BaseCycle.NOM).exists() },
            ),
        )
    }

    private val baseMessagerie: BaseMessagerie by lazy { BaseMessagerie.creer(appContext) }
    private val baseSignal: BaseSignal by lazy { BaseSignal.creer(appContext) }
    private val compte by lazy { MagasinCompteChiffre(preferences) }

    /** Dépôt des messages, créé une fois le membre connu (après l'inscription). */
    private var depotMessages: MessagerieRepository? = null

    private val synchroniseur: Synchroniseur by lazy {
        Synchroniseur(
            api = ApiKtor.pour(BuildConfig.SERVEUR) { android.util.Log.w("Cocooning", "Serveur injoignable", it) },
            cle = CleAppareilKeystore(),
            compte = compte,
            moteur = MoteurSignal(StockageSignalRoom(baseSignal)),
            fournirDepot = ::depot,
        )
    }

    private val verrouDepot = kotlinx.coroutines.sync.Mutex()

    private suspend fun depot(): MessagerieRepository = verrouDepot.withLock {
        depotMessages ?: run {
            val membre = compte.lire() ?: error("Téléphone pas encore inscrit")
            MessagerieRepositoryLocal(SourceMessagesRoom(baseMessagerie), IdMembre(membre.membre)).also { depotMessages = it }
        }
    }

    /** Messagerie chiffrée de bout en bout avec le serveur Cocooning (ADR 0003 et 0004). */
    val messagerie: ServiceMessagerie by lazy { ServiceMessagerieReseau(synchroniseur, compte, ::depot) }

    val cycle: CycleRepository by lazy { CycleRepositoryLocal(SourceCyclesRoom(baseCycle)) }

    val accueil: AccueilRepository by lazy { AccueilRepositoryLocal(cycle) }

    val dependancesCycle: DependancesCycle by lazy { DependancesCycle(cycle) }

    /** Nombre de messages non lus, affiché en badge sur l'onglet Messages. Fictif. */
    val messagesNonLus: StateFlow<Int> = MutableStateFlow(2)

    /** Prénom du partenaire, annoncé par le bouton S.O.S. Fictif. */
    val prenomPartenaire: String = "Yassine"

    private companion object {
        const val NOM_PREFERENCES = "cocooning_preferences"
    }
}
