package com.bigboote.domain.values

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JvmInline
@Serializable(with = AgentId.Serializer::class)
value class AgentId(val value: String) {
    init {
        require(value.length <= MAX_SLUG_LENGTH) {
            "AgentId value must be at most $MAX_SLUG_LENGTH characters, got: ${value.length}"
        }
        // TODO: validate character set
    }

    override fun toString(): String = value

    companion object {

        private const val MAX_SLUG_LENGTH = 64

        fun generate(): AgentId = AgentId(NanoIdUtils.randomNanoId())

    }

    internal object Serializer : KSerializer<AgentId> {
        override val descriptor = PrimitiveSerialDescriptor("AgentId", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: AgentId) = encoder.encodeString(value.value)
        override fun deserialize(decoder: Decoder) = AgentId(decoder.decodeString())
    }
}

