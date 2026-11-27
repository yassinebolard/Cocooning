package fr.cocooning.feature.sauvegarde

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Quand rappeler d'exporter la sauvegarde. Tant que le serveur n'existe pas, le fichier exporté
 * vieillit : ce qui a été saisi après lui serait perdu avec le téléphone.
 */
object RappelExport {

    /** Le rappel revient quand la dernière sauvegarde a au moins cet âge. */
    const val INTERVALLE_JOURS = 30L

    /** « Plus tard » le cache pendant ce temps. */
    const val REPORT_JOURS = 7L

    fun estDu(dernierExport: LocalDate?, reporteJusquau: LocalDate?, aujourdhui: LocalDate): Boolean {
        if (reporteJusquau != null && aujourdhui.isBefore(reporteJusquau)) return false
        return dernierExport == null || ChronoUnit.DAYS.between(dernierExport, aujourdhui) >= INTERVALLE_JOURS
    }
}
