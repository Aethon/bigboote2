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
        require(value.startsWith(PREFIX)) { "AgentId must start with '$PREFIX'" }
    }

    override fun toString(): String = value

    companion object {
        private const val PREFIX = "agent:"

        fun generate(): AgentId = AgentId("$PREFIX${NanoIdUtils.randomNanoId()}")
    }

    internal object Serializer : KSerializer<AgentId> {
        override val descriptor = PrimitiveSerialDescriptor("AgentId", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: AgentId) = encoder.encodeString(value.value)
        override fun deserialize(decoder: Decoder) = AgentId(decoder.decodeString())
    }
}
