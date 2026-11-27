package fr.cocooning.core.chiffrement

import fr.cocooning.core.chiffrement.ExceptionChiffrement.Raison
import org.signal.libsignal.protocol.DuplicateMessageException
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.InvalidVersionException
import org.signal.libsignal.protocol.LegacyMessageException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.ReusedBaseKeyException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.fingerprint.Fingerprint
import org.signal.libsignal.protocol.fingerprint.FingerprintParsingException
import org.signal.libsignal.protocol.fingerprint.FingerprintVersionMismatchException
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper
import java.time.Clock
import java.time.Duration

/**
 * Chiffrement de bout en bout d'un téléphone avec le protocole Signal (ADR 0003) : PQXDH pour
 * ouvrir une session, puis Double Ratchet pour chaque message.
 *
 * Tout passe par des octets : l'appelant (la couche de transport) les échange avec le serveur.
 * Chaque opération est faite d'un seul tenant dans le [StockageSignal]. Les appels sont bloquants
 * et sérialisés : à appeler hors du fil principal.
 *
 * @param appareilLocal numéro de ce téléphone pour son membre (1 en V1).
 */
class MoteurSignal(
    private val stockage: StockageSignal,
    private val appareilLocal: Int = MagasinSignal.APPAREIL_PRINCIPAL,
    private val horloge: Clock = Clock.systemUTC(),
) {
    private val magasin = MagasinSignal(stockage)

    /** Crée l'identité de ce téléphone si elle n'existe pas. Renvoie vrai si elle vient d'être créée. */
    @Synchronized
    fun initialiser(): Boolean = stockage.enTransaction {
        if (magasin.aUneIdentite()) {
            false
        } else {
            magasin.creerIdentite(IdentityKeyPair.generate(), KeyHelper.generateRegistrationId(false))
            true
        }
    }

    /** Clé d'identité publique de ce téléphone. */
    @Synchronized
    fun identitePublique(): ByteArray = identite().publicKey.serialize()

    /**
     * Clés à publier sur le serveur, ou nul si rien n'a changé depuis la dernière publication.
     *
     * - Pré-clé signée et pré-clé Kyber renouvelées tous les [renouvellement] jours ; les deux
     *   précédentes sont gardées, pour les messages encore en route qui s'en servent.
     * - Pré-clés à usage unique complétées jusqu'à [nombre] quand il en reste moins de [seuil].
     */
    @Synchronized
    fun clesAPublier(
        nombre: Int = PRE_CLES_PAR_LOT,
        seuil: Int = SEUIL_RECHARGE,
        renouvellement: Duration = RENOUVELLEMENT,
    ): PublicationCles? = stockage.enTransaction {
        val paire = identite()
        val maintenant = horloge.millis()

        val signeeActuelle = magasin.dernierId(COMPTEUR_SIGNEE)?.let { id ->
            runCatching { magasin.loadSignedPreKey(id) }.getOrNull()
        }
        val aRenouveler = signeeActuelle == null || maintenant - signeeActuelle.timestamp >= renouvellement.toMillis()
        val signee = if (aRenouveler) nouvelleSignee(paire, maintenant) else signeeActuelle!!
        val kyber = if (aRenouveler) nouvelleKyber(paire, maintenant) else magasin.loadKyberPreKey(magasin.dernierId(COMPTEUR_KYBER)!!)
        if (aRenouveler) oublierAnciennes()

        val restantes = magasin.nombrePreCles()
        val uniques = if (restantes < seuil) List(nombre - restantes) { nouvellePreCle() } else emptyList()

        if (!aRenouveler && uniques.isEmpty()) {
            null
        } else {
            PublicationCles(
                idEnregistrement = magasin.localRegistrationId,
                appareil = appareilLocal,
                identite = paire.publicKey.serialize(),
                preCleSignee = PreClePublique(signee.id, signee.keyPair.publicKey.serialize(), signee.signature),
                preCleKyber = PreClePublique(kyber.id, kyber.keyPair.publicKey.serialize(), kyber.signature),
                preClesUniques = uniques.map { PreClePublique(it.id, it.keyPair.publicKey.serialize()) },
            )
        }
    }

    /**
     * Ouvre une session avec un appareil à partir du paquet que le serveur a donné. Le paquet doit
     * être signé par l'identité qu'il annonce ; sinon il est refusé.
     */
    @Synchronized
    fun ouvrirSession(destinataire: AdresseAppareil, paquet: PaquetDestinataire): ChangementIdentite =
        stockage.enTransaction {
            identite()
            val adresse = destinataire.versSignal()
            val bundle = traduire(Raison.PaquetInvalide) {
                PreKeyBundle(
                    paquet.idEnregistrement,
                    paquet.appareil,
                    paquet.preCleUnique?.id ?: PreKeyBundle.NULL_PRE_KEY_ID,
                    paquet.preCleUnique?.let { ECPublicKey(it.cle) },
                    paquet.preCleSignee.id,
                    ECPublicKey(paquet.preCleSignee.cle),
                    checkNotNull(paquet.preCleSignee.signature) { "Pré-clé signée sans signature" },
                    IdentityKey(paquet.identite),
                    paquet.preCleKyber.id,
                    KEMPublicKey(paquet.preCleKyber.cle),
                    checkNotNull(paquet.preCleKyber.signature) { "Pré-clé Kyber sans signature" },
                )
            }
            avecIdentite(adresse, bundle.identityKey) {
                traduire(Raison.PaquetInvalide) { SessionBuilder(magasin, adresse, adresseLocale()).process(bundle, horloge.instant()) }
            }
        }

    @Synchronized
    fun aUneSession(appareil: AdresseAppareil): Boolean = magasin.containsSession(appareil.versSignal())

    /** Chiffre [clair] pour un appareil avec qui une session est ouverte. */
    @Synchronized
    fun chiffrer(destinataire: AdresseAppareil, clair: ByteArray): EnveloppeChiffree = stockage.enTransaction {
        identite()
        val adresse = destinataire.versSignal()
        if (!magasin.containsSession(adresse)) throw ExceptionChiffrement(Raison.PasDeSession)
        val chiffre = traduire(Raison.PasDeSession) {
            SessionCipher(magasin, adresseLocale(), adresse).encrypt(Rembourrage.ajouter(clair), horloge.instant())
        }
        val type = when (chiffre.type) {
            CiphertextMessage.PREKEY_TYPE -> TypeEnveloppe.OuvertureSession
            CiphertextMessage.WHISPER_TYPE -> TypeEnveloppe.Message
            else -> error("Type de message Signal inattendu : ${chiffre.type}")
        }
        EnveloppeChiffree(type, chiffre.serialize())
    }

    /**
     * Déchiffre un message d'un appareil. Un premier message ouvre la session ; s'il vient d'une
     * identité différente de celle retenue, elle la remplace et le résultat le signale.
     */
    @Synchronized
    fun dechiffrer(expediteur: AdresseAppareil, enveloppe: EnveloppeChiffree): MessageDechiffre =
        stockage.enTransaction {
            identite()
            val adresse = expediteur.versSignal()
            val chiffreur = SessionCipher(magasin, adresseLocale(), adresse)
            when (enveloppe.type) {
                TypeEnveloppe.OuvertureSession -> {
                    val message = traduire(Raison.MessageInvalide) { PreKeySignalMessage(enveloppe.octets) }
                    var clair = ByteArray(0)
                    val changement = avecIdentite(adresse, message.identityKey) {
                        clair = traduire(Raison.MessageInvalide) { chiffreur.decrypt(message) }
                    }
                    MessageDechiffre(Rembourrage.retirer(clair), changement)
                }
                TypeEnveloppe.Message -> {
                    val message = traduire(Raison.MessageInvalide) { SignalMessage(enveloppe.octets) }
                    val clair = traduire(Raison.MessageInvalide) { chiffreur.decrypt(message) }
                    MessageDechiffre(Rembourrage.retirer(clair), ChangementIdentite.Inchangee)
                }
            }
        }

    /**
     * Code de sécurité entre ce téléphone (membre [moi]) et l'appareil d'[autre], dont l'identité
     * publique est [identiteAutre]. Les deux téléphones obtiennent les mêmes chiffres.
     */
    @Synchronized
    fun codeSecurite(moi: String, autre: String, identiteAutre: ByteArray): CodeSecurite {
        val empreinte = empreinte(moi, autre, identiteAutre)
        val chiffres = empreinte.displayableFingerprint.displayText.chunked(5).joinToString(" ")
        return CodeSecurite(chiffres, empreinte.scannableFingerprint.serialized)
    }

    /** Vrai si le QR code scanné sur l'autre téléphone correspond à ce que ce téléphone attend. */
    @Synchronized
    fun qrCorrespond(moi: String, autre: String, identiteAutre: ByteArray, qrScanne: ByteArray): Boolean = try {
        empreinte(moi, autre, identiteAutre).scannableFingerprint.compareTo(qrScanne)
    } catch (e: FingerprintVersionMismatchException) {
        false
    } catch (e: FingerprintParsingException) {
        false
    }

    private fun empreinte(moi: String, autre: String, identiteAutre: ByteArray): Fingerprint =
        NumericFingerprintGenerator(ITERATIONS_EMPREINTE).createFor(
            VERSION_EMPREINTE,
            moi.toByteArray(Charsets.UTF_8),
            identite().publicKey,
            autre.toByteArray(Charsets.UTF_8),
            traduire(Raison.PaquetInvalide) { IdentityKey(identiteAutre) },
        )

    /**
     * Retient [cle] pour [adresse] avant [action], puis revient à l'identité précédente si
     * l'action échoue : un message falsifié ne doit pas pouvoir changer l'identité retenue.
     */
    private fun avecIdentite(
        adresse: SignalProtocolAddress,
        cle: IdentityKey,
        action: () -> Unit,
    ): ChangementIdentite {
        val precedente = magasin.getIdentity(adresse)
        val changement = when {
            precedente == null -> ChangementIdentite.Nouvelle
            precedente == cle -> ChangementIdentite.Inchangee
            else -> ChangementIdentite.Changee
        }
        if (changement != ChangementIdentite.Inchangee) magasin.saveIdentity(adresse, cle)
        try {
            action()
        } catch (e: Throwable) {
            if (changement == ChangementIdentite.Changee) magasin.saveIdentity(adresse, precedente!!)
            if (changement == ChangementIdentite.Nouvelle) stockage.supprimerIdentiteDistante(adresse.name, adresse.deviceId)
            throw e
        }
        return changement
    }

    private fun nouvelleSignee(paire: IdentityKeyPair, maintenant: Long): SignedPreKeyRecord {
        val cles = ECKeyPair.generate()
        val signature = paire.privateKey.calculateSignature(cles.publicKey.serialize())
        return SignedPreKeyRecord(magasin.prochainId(COMPTEUR_SIGNEE), maintenant, cles, signature)
            .also { magasin.storeSignedPreKey(it.id, it) }
    }

    private fun nouvelleKyber(paire: IdentityKeyPair, maintenant: Long): KyberPreKeyRecord {
        val cles = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val signature = paire.privateKey.calculateSignature(cles.publicKey.serialize())
        return KyberPreKeyRecord(magasin.prochainId(COMPTEUR_KYBER), maintenant, cles, signature)
            .also { magasin.storeKyberPreKey(it.id, it) }
    }

    private fun nouvellePreCle(): PreKeyRecord =
        PreKeyRecord(magasin.prochainId(COMPTEUR_UNIQUE), ECKeyPair.generate()).also { magasin.storePreKey(it.id, it) }

    /** Garde la clé courante et les [ANCIENNES_GARDEES] précédentes, signées et Kyber. */
    private fun oublierAnciennes() {
        magasin.loadSignedPreKeys().sortedByDescending { it.timestamp }.drop(ANCIENNES_GARDEES + 1)
            .forEach { magasin.removeSignedPreKey(it.id) }
        magasin.loadKyberPreKeys().sortedByDescending { it.timestamp }.drop(ANCIENNES_GARDEES + 1)
            .forEach { magasin.supprimerKyber(it.id) }
    }

    private fun identite(): IdentityKeyPair {
        if (!magasin.aUneIdentite()) throw ExceptionChiffrement(Raison.IdentiteAbsente)
        return magasin.identityKeyPair
    }

    /**
     * Adresse Signal de ce téléphone. Attention, l'ordre diffère : `SessionBuilder` prend l'adresse distante
     * puis la locale, `SessionCipher` la locale puis la distante (vérifié par les tests).
     */
    private fun adresseLocale() = SignalProtocolAddress(NOM_LOCAL, appareilLocal)

    private fun AdresseAppareil.versSignal() = SignalProtocolAddress(membre, appareil)

    /** Traduit les exceptions de libsignal en [ExceptionChiffrement]. */
    private inline fun <R> traduire(raison: Raison, bloc: () -> R): R = try {
        bloc()
    } catch (e: ExceptionChiffrement) {
        throw e
    } catch (e: DuplicateMessageException) {
        throw ExceptionChiffrement(Raison.MessageEnDouble, e)
    } catch (e: ReusedBaseKeyException) {
        throw ExceptionChiffrement(Raison.MessageEnDouble, e)
    } catch (e: NoSessionException) {
        throw ExceptionChiffrement(Raison.PasDeSession, e)
    } catch (e: UntrustedIdentityException) {
        throw ExceptionChiffrement(raison, e)
    } catch (e: InvalidKeyIdException) {
        throw ExceptionChiffrement(raison, e)
    } catch (e: InvalidMessageException) {
        throw ExceptionChiffrement(raison, e)
    } catch (e: InvalidKeyException) {
        throw ExceptionChiffrement(raison, e)
    } catch (e: InvalidVersionException) {
        throw ExceptionChiffrement(raison, e)
    } catch (e: LegacyMessageException) {
        throw ExceptionChiffrement(raison, e)
    } catch (e: IllegalStateException) {
        throw ExceptionChiffrement(raison, e)
    } catch (e: IllegalArgumentException) {
        throw ExceptionChiffrement(raison, e)
    }

    companion object {
        const val PRE_CLES_PAR_LOT = 100
        const val SEUIL_RECHARGE = 20
        val RENOUVELLEMENT: Duration = Duration.ofDays(7)
        private const val ANCIENNES_GARDEES = 2
        private const val ITERATIONS_EMPREINTE = 5200
        private const val VERSION_EMPREINTE = 2
        private const val NOM_LOCAL = "moi"
        private const val COMPTEUR_SIGNEE = "pre_cle_signee"
        private const val COMPTEUR_KYBER = "pre_cle_kyber"
        private const val COMPTEUR_UNIQUE = "pre_cle_unique"
    }
}
