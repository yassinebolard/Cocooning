package com.agdc.cocooning.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/*
 * Clés des onglets qui n'ont pas encore leur module. Chacune partira dans son module
 * `:feature:…` quand l'onglet sera écrit, comme `AccueilCle` dans `:feature:accueil`
 * et `CycleCle` dans `:feature:cycle`.
 */

@Serializable
data object MessagesCle : NavKey

@Serializable
data object CalendrierCle : NavKey

@Serializable
data object NotesCle : NavKey
