package com.agdc.cocooning.navigation

import androidx.navigation3.runtime.NavKey
import fr.cocooning.designsystem.components.DestinationNav
import fr.cocooning.designsystem.components.destinationsPrincipales
import fr.cocooning.feature.accueil.AccueilCle
import fr.cocooning.feature.cycle.CycleCle

/**
 * Configuration de la barre de navigation : les cinq onglets de premier niveau, dans l'ordre du
 * cahier des charges (Accueil, Messages, Cycle, Notes, Calendrier). Libellés et icônes viennent de
 * [destinationsPrincipales] ; ce fichier n'ajoute que la clé de navigation de chaque onglet.
 */
object OngletsPrincipaux {
    /** Clés dans l'ordre des destinations de [destinationsPrincipales]. */
    val cles: List<NavKey> = listOf(AccueilCle, MessagesCle, CycleCle, NotesCle, CalendrierCle)

    /** Onglet de départ, et racine de la pile : le retour depuis un autre onglet y ramène. */
    val depart: NavKey = AccueilCle

    fun destinations(nonLus: Int): List<DestinationNav> =
        destinationsPrincipales(nonLus).also {
            check(it.size == cles.size) { "Chaque destination de la barre doit avoir sa clé." }
        }
}
