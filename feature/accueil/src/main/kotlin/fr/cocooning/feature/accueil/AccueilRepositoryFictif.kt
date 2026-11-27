package fr.cocooning.feature.accueil

import fr.cocooning.designsystem.components.PhaseCycle
import fr.cocooning.designsystem.components.SegmentCycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/**
 * Données fictives, en attendant le serveur et la base locale. Les prénoms et les contenus sont
 * des exemples ; rien n'est enregistré ni envoyé.
 */
class AccueilRepositoryFictif(
    horloge: Clock = Clock.systemDefaultZone(),
    private val latenceMs: Long = 400,
) : AccueilRepository {

    private val etat = MutableStateFlow(exemple(LocalDate.now(horloge)))

    override fun donnees(): Flow<DonneesAccueil> = etat.onStart { delay(latenceMs) }

    override suspend fun basculerRegles() {
        etat.update { donnees ->
            val cycle = donnees.cycle ?: return@update donnees
            val nouveau = if (cycle.reglesEnCours) {
                cycle.copy(reglesEnCours = false, phase = PhaseCycle.Folliculaire, jour = 6)
            } else {
                cycle.copy(reglesEnCours = true, phase = PhaseCycle.Menstruelle, jour = 1)
            }
            donnees.copy(cycle = nouveau)
        }
    }

    private companion object {
        val Segments = listOf(
            SegmentCycle(PhaseCycle.Menstruelle, 1, 5),
            SegmentCycle(PhaseCycle.Folliculaire, 6, 13),
            SegmentCycle(PhaseCycle.Ovulatoire, 14, 16),
            SegmentCycle(PhaseCycle.Luteale, 17, 28),
        )

        fun exemple(aujourdhui: LocalDate) = DonneesAccueil(
            prenom = "Inès",
            prenomPartenaire = "Yassine",
            aujourdhui = aujourdhui,
            cycle = ResumeCycle(
                phase = PhaseCycle.Luteale,
                jour = 22,
                segments = Segments,
                prochainesRegles = aujourdhui.plusDays(6)..aujourdhui.plusDays(9),
                reglesEnCours = false,
            ),
            dernierMessage = ApercuMessage(
                auteur = "Yassine",
                heure = LocalTime.of(8, 12),
                texte = "Je passe prendre du pain en rentrant. Tu veux autre chose ?",
                nonLus = 2,
            ),
            prochainEvenement = ApercuEvenement(
                titre = "Dîner chez Léa et Karim",
                jour = aujourdhui,
                heure = LocalTime.of(19, 30),
                lieu = "Lyon 6e",
            ),
            notesEpinglees = listOf(
                ApercuNote("courses", "Courses", "Lait, œufs, tomates, café"),
                ApercuNote("week-end", "Idées de week-end", "Annecy, marché de Noël, rando au Salève"),
                ApercuNote("maison", "Maison", "Appeler le plombier avant jeudi"),
            ),
        )
    }
}
