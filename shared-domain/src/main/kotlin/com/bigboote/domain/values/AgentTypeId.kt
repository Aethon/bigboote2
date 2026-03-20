package com.bigboote.domain.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JvmInline
@Serializable(with = AgentTypeId.Serializer::class)
value class AgentTypeId(val value: String) {
    init {
        require(value.length <= MAX_SLUG_LENGTH) {
            "AgentTypeId slug must be at most $MAX_SLUG_LENGTH characters, got: ${value.length}"
        }
        require(value.matches(SLUG_REGEX)) {
            "AgentTypeId slug must be lowercase alphanumeric with hyphens, got: '$value'"
        }
    }

    override fun toString(): String = value

    companion object {
        private const val MAX_SLUG_LENGTH = 64
        private val SLUG_REGEX = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

    }

    internal object Serializer : KSerializer<AgentTypeId> {
        override val descriptor = PrimitiveSerialDescriptor("AgentTypeId", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: AgentTypeId) = encoder.encodeString(value.value)
        override fun deserialize(decoder: Decoder) = AgentTypeId(decoder.decodeString())
    }
}
