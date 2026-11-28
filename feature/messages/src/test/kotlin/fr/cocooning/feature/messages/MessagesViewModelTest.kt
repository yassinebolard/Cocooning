package fr.cocooning.feature.messages

import fr.cocooning.core.messagerie.domaine.ContenuMessage
import fr.cocooning.core.messagerie.domaine.IdConversation
import fr.cocooning.core.messagerie.domaine.IdMembre
import fr.cocooning.core.messagerie.domaine.IdMessage
import fr.cocooning.core.messagerie.domaine.Message
import fr.cocooning.core.messagerie.domaine.MessageEntrant
import fr.cocooning.core.messagerie.donnees.MessagerieRepositoryLocal
import fr.cocooning.core.messagerie.donnees.ResultatMessagerie
import fr.cocooning.core.messagerie.donnees.SourceMessagesMemoire
import fr.cocooning.core.reseau.ErreurReseau
import fr.cocooning.core.reseau.EtatInscription
import fr.cocooning.core.reseau.RapportSynchro
import fr.cocooning.core.reseau.ServiceMessagerie
import fr.cocooning.protocole.api.ErreurApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun avant() = Dispatchers.setMain(dispatcher)

    @After
    fun apres() = Dispatchers.resetMain()

    private class FauxService(var etatInitial: EtatInscription = EtatInscription.Inscrit) : ServiceMessagerie {
        val moi = IdMembre("moi")
        val elle = IdMembre("elle")
        val conversation = IdConversation("couple")
        val depot = MessagerieRepositoryLocal(SourceMessagesMemoire(), moi)
        var synchros = 0
        var horsLigne = false
        var erreurInscription: Exception? = null
        val accuses = mutableListOf<List<IdMessage>>()

        override suspend fun etat() = etatInitial
        override suspend fun creerEspace(): Pair<String, Long> {
            erreurInscription?.let { throw it }
            etatInitial = EtatInscription.Inscrit
            return "ABCD2345" to 0
        }
        override suspend fun rejoindre(code: String) {
            erreurInscription?.let { throw it }
        }
        override suspend fun reprendre() = Unit
        override suspend fun moi() = moi
        override fun conversation(): Flow<List<Message>> = depot.conversation(conversation)
        override suspend fun ecrire(texte: String, reponseA: IdMessage?) = depot.ecrire(conversation, listOf(elle), texte, reponseA)
        override suspend fun reessayer(id: IdMessage) = depot.reessayer(id)
        override suspend fun synchroniser(): RapportSynchro {
            synchros++
            if (horsLigne) throw ErreurReseau.HorsLigne(IOException())
            depot.aEnvoyer().forEach { depot.marquerEnvoye(it.id) }
            return RapportSynchro()
        }
        override suspend fun accuserLecture(messages: List<Message>) {
            accuses += messages.map { it.id }
        }
    }

    private fun TestScope.vm(service: ServiceMessagerie) = MessagesViewModel(service, intervalleSynchro = 1_000).also { runCurrent() }

    @Test
    fun `non inscrit, creation de l'espace puis code affiche`() = runTest(dispatcher) {
        val service = FauxService(EtatInscription.NonInscrit)
        val vm = vm(service)
        assertEquals(MessagesUiState.Inscription(), vm.etat.value)
        vm.creerEspace()
        runCurrent()
        val etat = vm.etat.value as MessagesUiState.Conversation
        assertEquals("ABCD2345", etat.invitation)
        vm.viewModelScope_annuler()
    }

    @Test
    fun `code invalide, message clair`() = runTest(dispatcher) {
        val service = FauxService(EtatInscription.NonInscrit)
        service.erreurInscription = ErreurReseau.Http(404, ErreurApi("invitation_invalide", ""))
        val vm = vm(service)
        vm.rejoindre("FAUXCODE")
        runCurrent()
        val etat = vm.etat.value as MessagesUiState.Inscription
        assertTrue(etat.erreur!!.contains("inconnu"))
        vm.rejoindre("  ")
        assertTrue((vm.etat.value as MessagesUiState.Inscription).erreur!!.contains("Saisis"))
    }

    @Test
    fun `envoyer vide la saisie, enregistre et synchronise`() = runTest(dispatcher) {
        val service = FauxService()
        val vm = vm(service)
        vm.modifierSaisie("Bonsoir")
        vm.envoyer()
        runCurrent()
        val etat = vm.etat.value as MessagesUiState.Conversation
        assertEquals("", etat.saisie)
        assertEquals(ContenuMessage.Texte("Bonsoir"), etat.messages.single().contenu)
        assertTrue(service.synchros >= 2)
        vm.viewModelScope_annuler()
    }

    @Test
    fun `hors ligne signale, puis efface au retour du reseau`() = runTest(dispatcher) {
        val service = FauxService()
        service.horsLigne = true
        val vm = vm(service)
        assertTrue((vm.etat.value as MessagesUiState.Conversation).horsLigne)
        service.horsLigne = false
        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(!(vm.etat.value as MessagesUiState.Conversation).horsLigne)
        vm.viewModelScope_annuler()
    }

    @Test
    fun `accuse lu une seule fois par message affiche`() = runTest(dispatcher) {
        val service = FauxService()
        service.depot.recevoir(MessageEntrant(IdMessage("e1"), service.conversation, service.elle, "Coucou", null, Instant.EPOCH))
        val vm = vm(service)
        val messages = (vm.etat.value as MessagesUiState.Conversation).messages
        vm.messagesAffiches(messages)
        vm.messagesAffiches(messages)
        runCurrent()
        assertEquals(listOf(listOf(IdMessage("e1"))), service.accuses)
        vm.viewModelScope_annuler()
    }

    @Test
    fun `un texte refuse est rendu a la saisie`() = runTest(dispatcher) {
        val service = object : ServiceMessagerie by FauxService() {
            override suspend fun ecrire(texte: String, reponseA: IdMessage?) =
                ResultatMessagerie.Refuse(fr.cocooning.core.messagerie.domaine.ErreurMessagerie.AucunDestinataire)
        }
        val vm = vm(service)
        vm.modifierSaisie("Perdu ?")
        vm.envoyer()
        runCurrent()
        assertEquals("Perdu ?", (vm.etat.value as MessagesUiState.Conversation).saisie)
        vm.viewModelScope_annuler()
    }

    /** La boucle de synchronisation tourne sans fin : on la coupe avant la fin du test. */
    @Suppress("RestrictedApi")
    private fun MessagesViewModel.viewModelScope_annuler() {
        androidx.lifecycle.ViewModelStore().apply { put("vm", this@viewModelScope_annuler) }.clear()
    }
}
