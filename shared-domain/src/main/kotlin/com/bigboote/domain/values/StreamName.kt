package com.bigboote.domain.values

import com.bigboote.domain.events.Event
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = AnyStreamNameSerializer::class)
sealed interface AnyStreamName {
    val path: String
}

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
sealed class StreamName<out E : Event> : AnyStreamName {

    // ── Effort lifecycle stream: /effort:{id} ─────────────────────────────
    data class Effort(val id: EffortId) : StreamName<com.bigboote.domain.events.EffortEvent>() {
        override val path = makePath(id)

        companion object {

            internal const val PREFIX = "eff:"
            internal fun tryFromInput(input: Input): StreamName<*>? {
                return input.whenPrefixed(PREFIX) { value ->
                    EffortId(value).let { effortId ->

                        input.doneOr({ Effort(effortId) }) { input ->

                            Docs.tryFromInput(input, effortId)
                                ?: Agent.tryFromInput(input, effortId)
                                ?: GroupChannel.tryFromInput(input, effortId)
                                ?: DirectMessage.tryFromInput(input, effortId)
                        }
                    }
                }
            }

            internal fun makePath(effortId: EffortId): String = "/${PREFIX}${effortId.value}"
        }
    }

    // ── AgentType configuration stream: /agenttype:{id} ──────────────────
    data class AgentType(val id: AgentTypeId) : StreamName<com.bigboote.domain.events.AgentTypeEvent>() {
        override val path = makePath(id)

        companion object {

            internal const val PREFIX = "agtyp:"
            internal fun tryFromInput(input: Input): StreamName<*>? {
                return input.whenPrefixed(PREFIX) { value -> input.requireDone { AgentType(AgentTypeId(value)) } }
            }

            internal fun makePath(agentTypeId: AgentTypeId): String = "/$PREFIX${agentTypeId.value}"
        }
    }

    // ── Agent lifecycle stream: /effort:{id}/agent:{id} ──────────────────
    // Events: AgentStarted, AgentStopped, AgentFailed, AgentPaused, AgentResumed
    data class Agent(
        val effortId: EffortId,
        val agentId: AgentId,
    ) : StreamName<com.bigboote.domain.events.AgentEvent>() {
        override val path = makePath(effortId, agentId)

        companion object {

            internal const val PREFIX = "agt:"
            internal fun tryFromInput(input: Input, effortId: EffortId): StreamName<*>? {
                return input.whenPrefixed(PREFIX) { value ->

                    AgentId(value).let { agentId ->
                        input.doneOr({ Agent(effortId, agentId) }) {
                            Loop.tryFromInput(input, effortId, agentId)
                        }
                    }
                }
            }

            internal fun makePath(effortId: EffortId, agentId: AgentId): String =
                "${Effort.makePath(effortId)}/$PREFIX${agentId.value}"
        }
    }

    // ── Loop execution stream: /effort:{id}/agent:{id}/loop ───────────────
    // Events: StepStarted, StepEnded, LLMRequestSent, LLMResponseReceived, etc.
    // 'loop' is a singleton segment — no ID, always a child of Agent.
    data class Loop(
        val effortId: EffortId,
        val agentId: AgentId,
    ) : StreamName<com.bigboote.domain.events.LoopEvent>() {
        override val path = makePath(effortId, agentId)

        companion object {

            internal const val VALUE = "loop"
            internal fun tryFromInput(input: Input, effortId: EffortId, agentId: AgentId): StreamName<*>? {
                return input.whenExactly(VALUE) { input.requireDone { Loop(effortId, agentId) } }
            }

            internal fun makePath(effortId: EffortId, agentId: AgentId): String =
                "${Agent.makePath(effortId, agentId)}/$VALUE"
        }
    }

    // ── Conversation stream: /effort:{id}/channel:{collaboratorName.simple} ─────────
    data class GroupChannel(
        val effortId: EffortId,
        val channelName: CollaboratorName.Channel,
    ) : StreamName<com.bigboote.domain.events.GroupChannelEvent>() {
        override val path = makePath(effortId, channelName)

        companion object {

            internal const val PREFIX = "grp:"
            internal fun tryFromInput(input: Input, effortId: EffortId): StreamName<*>? {
                return input.whenPrefixed(PREFIX) { value ->

                    CollaboratorName.Channel(value).let { channelName ->
                        input.requireDone({ GroupChannel(effortId, channelName) })
                    }
                }
            }

            internal fun makePath(effortId: EffortId, channelName: CollaboratorName.Channel): String =
                "${Effort.makePath(effortId)}/$PREFIX${channelName.simple}"
        }
    }

    // ── Conversation stream: /effort:{id}/dm:{collaboratorName.simple} ─────────
    data class DirectMessage(
        val effortId: EffortId,
        val collaboratorName: CollaboratorName.Individual,
    ) : StreamName<com.bigboote.domain.events.DirectMessageEvent>() {
        override val path = makePath(effortId, collaboratorName)

        companion object {

            internal const val PREFIX = "dm:"
            internal fun tryFromInput(input: Input, effortId: EffortId): StreamName<*>? {
                return input.whenPrefixed(PREFIX) { value ->

                    CollaboratorName.Individual(value).let { collaboratorName ->
                        input.requireDone({ DirectMessage(effortId, collaboratorName) })
                    }
                }
            }

            internal fun makePath(effortId: EffortId, collaboratorName: CollaboratorName.Individual): String =
                "${Effort.makePath(effortId)}/$PREFIX${collaboratorName.simple}"
        }
    }

    // ── Document stream: /effort:{id}/docs ────────────────────────────────
    // 'docs' is a singleton segment.
    data class Docs(val effortId: EffortId) : StreamName<com.bigboote.domain.events.DocumentEvent>() {
        override val path = makePath(effortId)

        companion object {

            internal const val VALUE = "doc"
            internal fun tryFromInput(input: Input, effortId: EffortId): StreamName<*>? {
                return input.whenExactly(VALUE) { input.requireDone { Docs(effortId) } }
            }

            internal fun makePath(effortId: EffortId): String = "${Effort.makePath(effortId)}/$VALUE"
        }
    }

    companion object {
        /**
         * Parse a stream path string back into a typed [StreamName].
         *
         * Throws [IllegalStateException] on unrecognised patterns — an unknown
         * path indicates a misconfigured subscription or a new stream type that
         * has not been added to this when block. Both cases should be loud.
         */
        fun parse(path: String): StreamName<*> {
            val parts = path.split("/")
            require(parts.size >= 2) { "Empty stream path" }
            require(parts[0] == "") { "Stream path must start with '/'" }

            val input = Input.of(parts.drop(1))

            AgentType.tryFromInput(input)?.let { return it }

            Effort.tryFromInput(input)?.let { return it }

            error("Unknown stream path: $path")
        }


//        private fun tryEffort(input: Input): StreamName<*>? {
//
//            return EffortId.tryParse(input.next)?.let { effortId ->
//
//                input.doneOr({ Effort(effortId) }) { input ->
//
//                    tryDocs(effortId, input)?.let { return@let it }
//
//                    tryAgent(effortId, input)?.let { return@let it }
//
//                    tryGroupChannel(effortId, input)?.let { return@let it }
//
//                    tryDirectMessages(effortId, input)?.let { return@let it }
//                }
//            }
//        }
//
//        private fun tryDocs(effortId: EffortId, input: Input): StreamName<*>? =
//            if (input.next != "docs")
//                null
//            else
//                input.requireDone { Docs(effortId) }

//        private fun tryAgent(effortId: EffortId, input: Input): StreamName<*>? {
//
//            return AgentId.tryParse(input.next)?.let { agentId ->
//
//                input.doneOr({ Agent(effortId, agentId) }) { input ->
//                    tryLoop(effortId, agentId, input)
//                }
//            }
//        }

//        private fun tryLoop(effortId: EffortId, agentId: AgentId, input: Input): StreamName<*>? =
//            if (input.next != "loop")
//                null
//            else
//                input.requireDone { Loop(effortId, agentId) }


//        private fun tryGroupChannel(effortId: EffortId, input: Input): StreamName<*>? {
//
//            if (!input.next.startsWith("channel:"))
//                return null
//
//            val channelName = CollaboratorName.Channel(input.next.removePrefix("channel:"))
//
//            return input.requireDone { GroupChannel(effortId, channelName) }
//        }
//
//        private fun tryDirectMessages(effortId: EffortId, input: Input): StreamName<*>? {
//
//            if (!input.next.startsWith("dm:")) return null
//
//            val collabName = CollaboratorName.Individual(input.next.removePrefix("dm:"))
//
//            return input.requireDone { DirectMessage(effortId, collabName) }
//        }

//        private fun requireDone(
//            next: String,
//            remaining: List<String>,
//            returnProduction: () -> StreamName<*>
//        ): StreamName<*> {
//            if (remaining.isEmpty()) returnProduction()
//            error("Unknown stream path pattern after $next")
//        }

        internal data class Input(val next: String, val remaining: List<String>) {

            /**
             * When the next token starts with [prefix], invokes [produce] with the prefix removed.
             *
             * @return `null` if the prefix does not match, otherwise the result of [produce]
             */
            fun whenPrefixed(prefix: String, produce: (String) -> StreamName<*>): StreamName<*>? {
                return if (next.startsWith(prefix))
                    produce(next.removePrefix(prefix))
                else
                    null
            }

            /**
             * When the next token is [value], invokes [produce] with [value].
             *
             * @return `null` if the value does not match, otherwise the result of [produce]
             */
            fun whenExactly(value: String, produce: (String) -> StreamName<*>): StreamName<*>? {
                return if (next == value)
                    produce(next.removePrefix(value))
                else
                    null
            }

            fun advance(): Input =
                if (remaining.isNotEmpty()) Input(remaining.first(), remaining.drop(1))
                else
                    error("Stream path is not complete")

            fun doneOr(produce: () -> StreamName<*>, produceNext: (Input) -> StreamName<*>?): StreamName<*> {
                return if (remaining.isEmpty())
                    produce()
                else
                    produceNext(advance()) ?: errorHere()
            }

            fun requireDone(produce: () -> StreamName<*>): StreamName<*> {
                return doneOr(produce) {
                    errorHere()
                }
            }

            fun errorHere(): Nothing =
                error("Unknown or unexpected stream path pattern: ${remaining.joinToString("/")}")

            companion object {
                fun of(parts: List<String>): Input =
                    Input(parts.first(), parts.drop(1))
            }
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

object AnyStreamNameSerializer : KSerializer<AnyStreamName> {
    override val descriptor = StreamNameSerializer.descriptor

    override fun serialize(encoder: Encoder, value: AnyStreamName) =
        encoder.encodeString(value.path)

    override fun deserialize(decoder: Decoder): AnyStreamName =
        StreamName.parse(decoder.decodeString())
}