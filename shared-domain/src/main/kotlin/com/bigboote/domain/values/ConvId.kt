package com.bigboote.domain.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Natural key for a Conversation. Serializes as a plain string.
 *
 * - Channel: "conv:#review"
 * - DirectMessage: "conv:@alice+@bob" (parties always sorted alphabetically)
 */
@Serializable(with = ConvIdSerializer::class)
sealed class ConvId {
    /** Full domain ID (e.g. "conv:#review" or "conv:@alice+@bob"). */
    abstract val value: String

    /** Stream-safe name with @ and # stripped for KurrentDB path safety. */
    abstract val streamSafeName: String

    data class Channel(val name: String) : ConvId() {
        override val value: String get() = "conv:#$name"
        override val streamSafeName: String get() = "conv:$name"
    }

    data class DirectMessage(val party1: String, val party2: String) : ConvId() {
        init {
            require(party1 <= party2) {
                "DirectMessage parties must be in sorted order. Use ConvId.dm() to construct."
            }
        }

        override val value: String get() = "conv:@$party1+@$party2"
        override val streamSafeName: String get() = "conv:$party1+$party2"
    }

    companion object {
        fun channel(name: String): Channel = Channel(name)

        fun dm(a: String, b: String): DirectMessage {
            val sorted = listOf(a, b).sorted()
            return DirectMessage(sorted[0], sorted[1])
        }

        fun parse(value: String): ConvId {
            require(value.startsWith("conv:")) { "ConvId must start with 'conv:', got: '$value'" }
            val body = value.removePrefix("conv:")
            return when {
                body.startsWith("#") -> Channel(body.removePrefix("#"))
                body.contains("+") -> {
                    val parts = body.split("+")
                    require(parts.size == 2) { "DirectMessage ConvId must have exactly two parties" }
                    dm(parts[0].removePrefix("@"), parts[1].removePrefix("@"))
                }
                else -> throw IllegalArgumentException("Cannot parse ConvId: '$value'")
            }
        }
    }
}

internal object ConvIdSerializer : KSerializer<ConvId> {
    override val descriptor = PrimitiveSerialDescriptor("ConvId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ConvId) =
        encoder.encodeString(value.value)

    override fun deserialize(decoder: Decoder): ConvId =
        ConvId.parse(decoder.decodeString())
}
