package com.bigboote.domain.values

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JvmInline
@Serializable(with = DocumentId.Serializer::class)
value class DocumentId(val value: String) {
    init {
        // TODO: validate format
    }

    override fun toString(): String = value

    companion object {

        fun generate(): DocumentId = DocumentId(NanoIdUtils.randomNanoId())
    }

    internal object Serializer : KSerializer<DocumentId> {
        override val descriptor = PrimitiveSerialDescriptor("DocumentId", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: DocumentId) = encoder.encodeString(value.value)
        override fun deserialize(decoder: Decoder) = DocumentId(decoder.decodeString())
    }
}
