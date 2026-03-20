package com.bigboote.domain.values

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JvmInline
@Serializable(with = MessageId.Serializer::class)
value class MessageId(val value: String) {
    init {
       // TODO: validate format
    }

    override fun toString(): String = value

    companion object {
        fun generate(): MessageId = MessageId(NanoIdUtils.randomNanoId())
    }

    internal object Serializer : KSerializer<MessageId> {
        override val descriptor = PrimitiveSerialDescriptor("MessageId", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: MessageId) = encoder.encodeString(value.value)
        override fun deserialize(decoder: Decoder) = MessageId(decoder.decodeString())
    }
}
