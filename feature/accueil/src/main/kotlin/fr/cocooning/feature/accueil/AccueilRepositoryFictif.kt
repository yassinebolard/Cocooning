package fr.cocooning.feature.accueil

import fr.cocooning.core.cycle.domaine.CycleSaisi
import fr.cocooning.core.cycle.donnees.CycleRepositoryLocal
import fr.cocooning.core.cycle.donnees.SourceCyclesMemoire
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/**
 * Accueil de démonstration, pour les aperçus et les tests : un historique de cycle en mémoire
 * (jour 22, phase lutéale) passé par les vrais calculs, et des contenus d'exemple. Rien n'est
 * enregistré ni envoyé.
 */
class AccueilRepositoryFictif(
    horloge: Clock = Clock.systemDefaultZone(),
    private val latenceMs: Long = 400,
) : AccueilRepository {

    private val aujourdhui = LocalDate.now(horloge)

    private val local = AccueilRepositoryLocal(
        cycles = CycleRepositoryLocal(SourceCyclesMemoire(historiqueExemple(aujourdhui))),
        horloge = horloge,
        jours = flowOf(aujourdhui),
    )

    override fun donnees(): Flow<DonneesAccueil> = local.donnees().onStart { delay(latenceMs) }

    override suspend fun basculerRegles(): ResultatBascule = local.basculerRegles()

    private companion object {
        /** Trois cycles de 28 jours avec des règles de 5 jours, le dernier commencé il y a 21 jours. */
        fun historiqueExemple(aujourdhui: LocalDate): List<CycleSaisi> {
            val debutCourant = aujourdhui.minusDays(21)
            return (3 downTo 0).map { i ->
                val debut = debutCourant.minusDays(28L * i)
                CycleSaisi(id = 4L - i, debut = debut, finRegles = debut.plusDays(4))
            }
        }
    }
}

/** Contenus d'exemple des blocs qui n'ont pas encore de module (messages, calendrier, notes). */
internal fun contenuFictifAccueil(aujourdhui: LocalDate) = DonneesAccueil(
    prenom = "Mymy",
    prenomPartenaire = "Yassine",
    aujourdhui = aujourdhui,
    cycle = null,
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
