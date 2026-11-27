package com.agdc.cocooning.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.cocooning.designsystem.components.DestinationNav
import fr.cocooning.designsystem.components.EmptyState

/** Onglet qui n'existe pas encore : il garde sa place dans la barre et le dit simplement. */
@Composable
fun OngletAVenir(
    destination: DestinationNav,
    message: String,
    modifier: Modifier = Modifier,
    margeBas: PaddingValues = PaddingValues(),
) {
    EmptyState(
        titre = destination.libelle,
        message = message,
        icone = destination.icone,
        modifier = modifier
            .fillMaxSize()
            .padding(margeBas),
    )
}
