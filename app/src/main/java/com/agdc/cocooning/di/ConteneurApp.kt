package com.agdc.cocooning.di

import android.content.Context
import fr.cocooning.core.cycle.donnees.CycleRepository
import fr.cocooning.core.cycle.donnees.CycleRepositoryLocal
import fr.cocooning.core.cycle.donnees.room.BaseCycle
import fr.cocooning.core.cycle.donnees.room.SourceCyclesRoom
import fr.cocooning.feature.accueil.AccueilRepository
import fr.cocooning.feature.accueil.AccueilRepositoryLocal
import fr.cocooning.feature.cycle.DependancesCycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Injection de dépendances à la main : un seul objet, créé par [com.agdc.cocooning.CocooningApplication],
 * qui fournit les dépôts aux ViewModel. Le cycle est stocké sur le téléphone (Room chiffré par SQLCipher) ; le reste
 * est encore fictif. Il sera remplacé par Hilt (ou complété) quand les autres données arriveront.
 */
class ConteneurApp(context: Context) {
    private val appContext = context.applicationContext

    private val baseCycle: BaseCycle by lazy { BaseCycle.creer(appContext) }

    val cycle: CycleRepository by lazy { CycleRepositoryLocal(SourceCyclesRoom(baseCycle)) }

    val accueil: AccueilRepository by lazy { AccueilRepositoryLocal(cycle) }

    val dependancesCycle: DependancesCycle by lazy { DependancesCycle(cycle) }

    /** Nombre de messages non lus, affiché en badge sur l'onglet Messages. Fictif. */
    val messagesNonLus: StateFlow<Int> = MutableStateFlow(2)

    /** Prénom du partenaire, annoncé par le bouton S.O.S. Fictif. */
    val prenomPartenaire: String = "Yassine"
}
