package com.agdc.cocooning.navigation

import androidx.navigation3.runtime.NavKey
import fr.cocooning.feature.accueil.AccueilCle
import fr.cocooning.feature.cycle.CycleCle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPrincipaleTest {

    private data object Detail : NavKey

    @Test
    fun `une pile vide commence a l'accueil`() {
        val pile = mutableListOf<NavKey>()
        val nav = NavigationPrincipale(pile)
        assertEquals(listOf<NavKey>(AccueilCle), pile)
        assertEquals(0, nav.indexOngletCourant)
    }

    @Test
    fun `changer d'onglet garde l'accueil en racine`() {
        val pile = mutableListOf<NavKey>(AccueilCle)
        val nav = NavigationPrincipale(pile)
        nav.ouvrirOnglet(MessagesCle)
        nav.ouvrir(Detail)
        nav.ouvrirOnglet(NotesCle)
        assertEquals(listOf(AccueilCle, NotesCle), pile)
        assertEquals(NotesCle, nav.ongletCourant)
        assertEquals(3, nav.indexOngletCourant)
    }

    @Test
    fun `un detail garde son onglet actif`() {
        val pile = mutableListOf<NavKey>(AccueilCle)
        val nav = NavigationPrincipale(pile)
        nav.ouvrirOnglet(CycleCle)
        nav.ouvrir(Detail)
        assertEquals(CycleCle, nav.ongletCourant)
    }

    @Test
    fun `le retour ramene a l'accueil puis rend la main au systeme`() {
        val pile = mutableListOf<NavKey>(AccueilCle)
        val nav = NavigationPrincipale(pile)
        nav.ouvrirOnglet(CalendrierCle)
        assertTrue(nav.retour())
        assertEquals(listOf<NavKey>(AccueilCle), pile)
        assertFalse(nav.retour())
    }

    @Test
    fun `revenir a l'accueil vide la pile au-dessus`() {
        val pile = mutableListOf<NavKey>(AccueilCle)
        val nav = NavigationPrincipale(pile)
        nav.ouvrirOnglet(MessagesCle)
        nav.ouvrirOnglet(0)
        assertEquals(listOf<NavKey>(AccueilCle), pile)
    }
}
