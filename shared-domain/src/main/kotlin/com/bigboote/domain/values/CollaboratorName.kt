package com.bigboote.domain.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Sealed class representing a collaborator address: @individual or #channel.
 * Serializes as a plain prefixed string (e.g. "@alice", "#review").
 */
@Serializable(with = CollaboratorNameSerializer::class)
sealed class CollaboratorName {
    abstract val simple: String

    data class Individual(override val simple: String) : CollaboratorName() {
        override fun toString(): String = "@$simple"
    }

    data class Channel(override val simple: String) : CollaboratorName() {
        override fun toString(): String = "#$simple"
    }

    companion object {
        fun from(string: String): CollaboratorName = when {
            string.startsWith("@") -> Individual(string.removePrefix("@"))
            string.startsWith("#") -> Channel(string.removePrefix("#"))
            else -> throw IllegalArgumentException(
                "CollaboratorName must start with '@' or '#', got: '$string'"
            )
        }
    }
}

internal object CollaboratorNameSerializer : KSerializer<CollaboratorName> {
    override val descriptor = PrimitiveSerialDescriptor("CollaboratorName", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CollaboratorName) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): CollaboratorName =
        CollaboratorName.from(decoder.decodeString())
}
