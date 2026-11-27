package com.agdc.cocooning.di

import fr.cocooning.feature.accueil.AccueilRepository
import fr.cocooning.feature.accueil.AccueilRepositoryFictif
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Injection de dépendances à la main : un seul objet, créé par [com.agdc.cocooning.CocooningApplication],
 * qui fournit les dépôts aux ViewModel. Toutes les implémentations sont fictives pour l'instant.
 * Il sera remplacé par Hilt (ou complété) quand la couche de données arrivera.
 */
class ConteneurApp {
    val accueil: AccueilRepository by lazy { AccueilRepositoryFictif() }

    /** Nombre de messages non lus, affiché en badge sur l'onglet Messages. Fictif. */
    val messagesNonLus: StateFlow<Int> = MutableStateFlow(2)

    /** Prénom du partenaire, annoncé par le bouton S.O.S. Fictif. */
    val prenomPartenaire: String = "Yassine"
}
