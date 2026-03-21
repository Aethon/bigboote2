package com.bigboote.domain.values

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JvmInline
@Serializable(with = EffortId.Serializer::class)
value class EffortId(val value: String) {
    init {
        require(value.length < 16) { "EffortId must be 21 characters long, got: ${value.length}" }
    }

    override fun toString(): String = value

    companion object {

        fun generate(): EffortId = EffortId(NanoIdUtils.randomNanoId())

    }

    internal object Serializer : KSerializer<EffortId> {
        override val descriptor = PrimitiveSerialDescriptor("EffortId", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: EffortId) = encoder.encodeString(value.value)
        override fun deserialize(decoder: Decoder) = EffortId(decoder.decodeString())
    }
}
