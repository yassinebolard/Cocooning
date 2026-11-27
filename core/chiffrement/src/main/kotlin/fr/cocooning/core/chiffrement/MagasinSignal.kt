package fr.cocooning.core.chiffrement

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.ReusedBaseKeyException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.util.UUID

/**
 * L'état Signal de ce téléphone, au format attendu par libsignal, rangé dans un [StockageSignal].
 *
 * Confiance dans les identités : la première clé vue pour un appareil est retenue (« confiance au
 * premier usage »). Une clé différente n'est jamais acceptée en silence par libsignal ; c'est
 * [MoteurSignal] qui l'enregistre explicitement et prévient l'utilisateur que le code de sécurité
 * a changé (EF-CPT-09).
 */
internal class MagasinSignal(private val stockage: StockageSignal) : SignalProtocolStore {

    fun aUneIdentite(): Boolean = stockage.identiteLocale() != null

    fun creerIdentite(paire: IdentityKeyPair, idEnregistrement: Int) =
        stockage.enregistrerIdentiteLocale(StockageSignal.IdentiteLocale(paire.serialize(), idEnregistrement))

    fun nombrePreCles(): Int = stockage.nombrePreCles()

    /** Identifiant suivant d'un compteur, de 1 à 16 777 215 puis retour à 1, comme Signal. */
    fun prochainId(compteur: String): Int {
        val suivant = (stockage.compteur(compteur) ?: 0) % ID_MAX + 1
        stockage.enregistrerCompteur(compteur, suivant)
        return suivant
    }

    fun dernierId(compteur: String): Int? = stockage.compteur(compteur)

    fun supprimerKyber(id: Int) = stockage.supprimerPreCleKyber(id)

    // IdentityKeyStore

    override fun getIdentityKeyPair(): IdentityKeyPair =
        IdentityKeyPair(checkNotNull(stockage.identiteLocale()) { "Identité locale absente" }.paire)

    override fun getLocalRegistrationId(): Int =
        checkNotNull(stockage.identiteLocale()) { "Identité locale absente" }.idEnregistrement

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): IdentityKeyStore.IdentityChange {
        val ancienne = getIdentity(address)
        stockage.enregistrerIdentiteDistante(address.name, address.deviceId, identityKey.serialize())
        return if (ancienne != null && ancienne != identityKey) {
            IdentityKeyStore.IdentityChange.REPLACED_EXISTING
        } else {
            IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
        }
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean {
        val connue = getIdentity(address)
        return connue == null || connue == identityKey
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? =
        stockage.identiteDistante(address.name, address.deviceId)?.let(::IdentityKey)

    // PreKeyStore

    override fun loadPreKey(preKeyId: Int): PreKeyRecord =
        PreKeyRecord(stockage.preCle(preKeyId) ?: throw InvalidKeyIdException("Pré-clé inconnue : $preKeyId"))

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) = stockage.enregistrerPreCle(preKeyId, record.serialize())
    override fun containsPreKey(preKeyId: Int): Boolean = stockage.preCle(preKeyId) != null
    override fun removePreKey(preKeyId: Int) = stockage.supprimerPreCle(preKeyId)

    // SignedPreKeyStore

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord = SignedPreKeyRecord(
        stockage.preCleSignee(signedPreKeyId) ?: throw InvalidKeyIdException("Pré-clé signée inconnue : $signedPreKeyId"),
    )

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> = stockage.preClesSignees().map(::SignedPreKeyRecord)

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) =
        stockage.enregistrerPreCleSignee(signedPreKeyId, record.serialize())

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = stockage.preCleSignee(signedPreKeyId) != null
    override fun removeSignedPreKey(signedPreKeyId: Int) = stockage.supprimerPreCleSignee(signedPreKeyId)

    // KyberPreKeyStore

    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord = KyberPreKeyRecord(
        stockage.preCleKyber(kyberPreKeyId) ?: throw InvalidKeyIdException("Pré-clé Kyber inconnue : $kyberPreKeyId"),
    )

    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> = stockage.preClesKyber().map(::KyberPreKeyRecord)

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) =
        stockage.enregistrerPreCleKyber(kyberPreKeyId, record.serialize())

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean = stockage.preCleKyber(kyberPreKeyId) != null

    /**
     * La pré-clé Kyber est « de dernier recours » : elle sert à plusieurs ouvertures de session et
     * n'est pas effacée. Un même trio Kyber, pré-clé signée et clé de base vu deux fois trahit un
     * message rejoué : il est refusé.
     */
    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) {
        if (stockage.noterUsageKyber(kyberPreKeyId, signedPreKeyId, baseKey.serialize())) {
            throw ReusedBaseKeyException("Ouverture de session rejouée")
        }
    }

    // SessionStore

    override fun loadSession(address: SignalProtocolAddress): SessionRecord =
        stockage.session(address.name, address.deviceId)?.let(::SessionRecord) ?: SessionRecord()

    override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> = addresses.map {
        stockage.session(it.name, it.deviceId)?.let(::SessionRecord) ?: throw NoSessionException("Pas de session : $it")
    }

    /** Appareils secondaires seulement : l'appareil principal porte le numéro 1 (convention Signal). */
    override fun getSubDeviceSessions(name: String): List<Int> =
        stockage.appareilsAvecSession(name).filter { it != APPAREIL_PRINCIPAL }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) =
        stockage.enregistrerSession(address.name, address.deviceId, record.serialize())

    override fun containsSession(address: SignalProtocolAddress): Boolean =
        stockage.session(address.name, address.deviceId) != null

    override fun deleteSession(address: SignalProtocolAddress) = stockage.supprimerSession(address.name, address.deviceId)
    override fun deleteAllSessions(name: String) = stockage.supprimerSessions(name)

    // SenderKeyStore (groupes, pas utilisé en V1 mais exigé par libsignal)

    override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) =
        stockage.enregistrerCleExpediteur(sender.name, sender.deviceId, distributionId.toString(), record.serialize())

    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? =
        stockage.cleExpediteur(sender.name, sender.deviceId, distributionId.toString())?.let(::SenderKeyRecord)

    companion object {
        const val APPAREIL_PRINCIPAL = 1
        private const val ID_MAX = 0xFFFFFF
    }
}
