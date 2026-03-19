package com.bigboote.domain.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Typed stream name hierarchy for KurrentDB.
 *
 * Each subclass encodes the context implied by its stream path (e.g. which
 * EffortId, AgentId, ConvId) so that Reactors and Projections can extract
 * that context from [com.bigboote.events.eventstore.EventEnvelope.streamName]
 * rather than duplicating it in every event payload.
 *
 * The type parameter [E] is the sealed event base for that stream.
 * It is used at the [com.bigboote.events.eventstore.EventStore] interface boundary
 * to enforce compile-time alignment between stream and event list.
 *
 * [streamAs] on each event sealed base (e.g. EffortEvent.streamAs()) is the
 * canonical way to cast an untyped StreamName to the correct subtype with a
 * clear error message on mismatch.
 *
 * Serializes to/from its [path] string via [StreamNameSerializer].
 *
 * See Architecture doc Change Document v1.0 Section 2.
 */
@Serializable(with = StreamNameSerializer::class)
sealed class StreamName<out E : Any> {
    abstract val path: String

    // ── Effort lifecycle stream: /effort:{id} ─────────────────────────────
    data class Effort(val id: EffortId) : StreamName<com.bigboote.domain.events.EffortEvent>() {
        override val path = "/effort:${id.value}"
    }

    // ── AgentType configuration stream: /agenttype:{id} ──────────────────
    data class AgentType(val id: AgentTypeId) : StreamName<com.bigboote.domain.events.AgentTypeEvent>() {
        override val path = "/agenttype:${id.value}"
    }

    // ── Agent lifecycle stream: /effort:{id}/agent:{id} ──────────────────
    // Events: AgentStarted, AgentStopped, AgentFailed, AgentPaused, AgentResumed
    data class Agent(
        val effortId: EffortId,
        val agentId: AgentId,
    ) : StreamName<com.bigboote.domain.events.AgentEvent>() {
        override val path = "/effort:${effortId.value}/agent:${agentId.value}"
    }

    // ── Loop execution stream: /effort:{id}/agent:{id}/loop ───────────────
    // Events: StepStarted, StepEnded, LLMRequestSent, LLMResponseReceived, etc.
    // 'loop' is a singleton segment — no ID, always a child of Agent.
    data class Loop(
        val effortId: EffortId,
        val agentId: AgentId,
    ) : StreamName<com.bigboote.domain.events.LoopEvent>() {
        override val path = "/effort:${effortId.value}/agent:${agentId.value}/loop"
    }

    // ── Conversation stream: /effort:{id}/{convId.streamSafeName} ─────────
    data class Conversation(
        val effortId: EffortId,
        val convId: ConvId,
    ) : StreamName<com.bigboote.domain.events.ConversationEvent>() {
        override val path = "/effort:${effortId.value}/${convId.streamSafeName}"
    }

    // ── Document stream: /effort:{id}/docs ────────────────────────────────
    // 'docs' is a singleton segment.
    data class Docs(val effortId: EffortId) : StreamName<com.bigboote.domain.events.DocumentEvent>() {
        override val path = "/effort:${effortId.value}/docs"
    }

    companion object {
        /**
         * Parse a stream path string back into a typed [StreamName].
         *
         * Throws [IllegalStateException] on unrecognised patterns — an unknown
         * path indicates a misconfigured subscription or a new stream type that
         * has not been added to this when block. Both cases should be loud.
         */
        fun parse(path: String): StreamName<*> = when {
            // /agenttype:{id}
            path.matches(Regex("/agenttype:.+$")) ->
                AgentType(AgentTypeId(path.removePrefix("/")))

            // /effort:{id}/agent:{id}/loop
            path.matches(Regex("/effort:[^/]+/agent:[^/]+/loop$")) ->
                path.split("/").drop(1).let {
                    Loop(
                        effortId = EffortId(it[0].removePrefix("effort:")),
                        agentId  = AgentId(it[1].removePrefix("agent:")),
                    )
                }

            // /effort:{id}/agent:{id}
            path.matches(Regex("/effort:[^/]+/agent:[^/]+$")) ->
                path.split("/").drop(1).let {
                    Agent(
                        effortId = EffortId(it[0].removePrefix("effort:")),
                        agentId  = AgentId(it[1].removePrefix("agent:")),
                    )
                }

            // /effort:{id}/conv:...
            path.matches(Regex("/effort:[^/]+/conv:.+$")) ->
                path.split("/", limit = 3).let {
                    Conversation(
                        effortId = EffortId(it[1].removePrefix("effort:")),
                        convId   = ConvId.parse(it[2]),
                    )
                }

            // /effort:{id}/docs
            path.matches(Regex("/effort:[^/]+/docs$")) ->
                Docs(EffortId(path.substringAfter("/effort:").substringBefore("/docs")))

            // /effort:{id}  — must be last to avoid matching prefix of longer paths
            path.matches(Regex("/effort:[^/]+$")) ->
                Effort(EffortId(path.removePrefix("/effort:")))

            else -> error("Unknown stream path pattern: $path")
        }
    }
}

/**
 * Serializer that represents a [StreamName] as its [StreamName.path] string.
 * Used when [StreamName] values are embedded in serialized DTOs.
 */
object StreamNameSerializer : KSerializer<StreamName<*>> {
    override val descriptor =
        PrimitiveSerialDescriptor("StreamName", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: StreamName<*>) =
        encoder.encodeString(value.path)

    override fun deserialize(decoder: Decoder): StreamName<*> =
        StreamName.parse(decoder.decodeString())
}
