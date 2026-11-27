package fr.cocooning.protocole

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Base64

/** Octets écrits en Base64 (sans retour à la ligne) dans le JSON de l'API. */
typealias Octets = @Serializable(with = SerialiseurBase64::class) ByteArray

object SerialiseurBase64 : KSerializer<ByteArray> {
    override val descriptor = PrimitiveSerialDescriptor("fr.cocooning.Octets", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray) =
        encoder.encodeString(Base64.getEncoder().encodeToString(value))

    override fun deserialize(decoder: Decoder): ByteArray = try {
        Base64.getDecoder().decode(decoder.decodeString())
    } catch (e: IllegalArgumentException) {
        throw kotlinx.serialization.SerializationException("Base64 invalide", e)
    }
}
