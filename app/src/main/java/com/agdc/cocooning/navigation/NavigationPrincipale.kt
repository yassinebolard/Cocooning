package com.agdc.cocooning.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Règles de déplacement dans l'application, appliquées à la pile de [NavDisplay].
 *
 * La pile commence toujours par l'accueil. Changer d'onglet remplace tout ce qui suit l'accueil
 * par l'onglet choisi : le retour système ramène donc à l'accueil, puis quitte l'application
 * (comportement recommandé par Material pour une barre de navigation). Les écrans de détail
 * s'empileront au-dessus de leur onglet avec [ouvrir].
 */
class NavigationPrincipale(
    private val pile: MutableList<NavKey>,
    private val onglets: List<NavKey> = OngletsPrincipaux.cles,
    private val depart: NavKey = OngletsPrincipaux.depart,
) {
    init {
        if (pile.firstOrNull() != depart) {
            pile.clear()
            pile.add(depart)
        }
    }

    /** Onglet affiché : le dernier onglet de la pile. */
    val ongletCourant: NavKey
        get() = pile.lastOrNull { it in onglets } ?: depart

    val indexOngletCourant: Int
        get() = onglets.indexOf(ongletCourant)

    fun ouvrirOnglet(cle: NavKey) {
        require(cle in onglets) { "$cle n'est pas un onglet de premier niveau." }
        while (pile.size > 1) pile.removeAt(pile.lastIndex)
        if (cle != depart) pile.add(cle)
    }

    fun ouvrirOnglet(index: Int) = ouvrirOnglet(onglets[index])

    /** Écran de détail au-dessus de l'onglet courant. */
    fun ouvrir(cle: NavKey) {
        pile.add(cle)
    }

    /** Retire l'écran du dessus. Faux quand il ne reste que l'accueil : le système ferme l'appli. */
    fun retour(): Boolean {
        if (pile.size <= 1) return false
        pile.removeAt(pile.lastIndex)
        return true
    }
}
