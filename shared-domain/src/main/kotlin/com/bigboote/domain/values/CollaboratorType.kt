package com.bigboote.domain.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(/*with = CollaboratorTypeSerializer::class*/)
enum class CollaboratorType {

    /**
     * The collaborator is an agent managed within the Bigboote system.
     */
    @SerialName("agent")
    AGENT,

    /**
     * The collaborator is external to the Bigboote system.
     */
    @SerialName("external")
    EXTERNAL

}

//object CollaboratorTypeSerializer : KSerializer<CollaboratorType> {
//    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CollaboratorType", PrimitiveKind.STRING)
//
//    override fun serialize(encoder: Encoder, value: CollaboratorType) =
//        encoder.encodeString(
//            when (value) {
//                CollaboratorType.AGENT -> "agent"
//                CollaboratorType.EXTERNAL -> "external"
//            }
//        )
//
//    override fun deserialize(decoder: Decoder): CollaboratorType =
//        when (val str = decoder.decodeString()) {
//            "agent" -> CollaboratorType.AGENT
//            "external" -> CollaboratorType.EXTERNAL
//            else -> throw IllegalArgumentException("$str is not a valid CollaboratorType.")
//        }
//}