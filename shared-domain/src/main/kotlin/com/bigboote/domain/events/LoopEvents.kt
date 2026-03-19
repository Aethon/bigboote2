package com.bigboote.domain.events

import com.bigboote.domain.aggregates.AssistantStatus
import com.bigboote.domain.aggregates.LoopStatus
import com.bigboote.domain.values.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Events for the Loop execution stream: `/effort:{id}/agent:{id}/loop`
 *
 * Both [AgentId] and [EffortId] are inherent to [StreamName.Loop] and are no longer
 * duplicated in event payloads. Retrieve them via [StreamName.Loop.agentId] and
 * [StreamName.Loop.effortId] from [com.bigboote.events.eventstore.EventEnvelope.streamName].
 *
 * [LLMRequestSent] and [LLMResponseReceived] moved here from [AgentEvent] — they are
 * execution-scoped events that belong on this stream rather than the lifecycle stream.
 *
 * See Architecture doc Change Document v1.0 Section 5.3 / 5.4.
 */
@Serializable
sealed interface LoopEvent {

    @Serializable
    @SerialName("StepStarted")
    data class StepStarted(
        val startedAt: Instant,
    ) : LoopEvent

    @Serializable
    @SerialName("StepEnded")
    data class StepEnded(
        val result: LoopStatus,
        val endedAt: Instant,
    ) : LoopEvent

    @Serializable
    @SerialName("AssistantTurnSucceeded")
    data class AssistantTurnSucceeded(
        val newMessage: JsonElement? = null,
        val response: JsonElement,
        val assistantStatus: AssistantStatus,
        val satisfiedContentIds: List<String> = emptyList(),
        val occurredAt: Instant,
    ) : LoopEvent

    @Serializable
    @SerialName("AssistantTurnFailed")
    data class AssistantTurnFailed(
        val newMessage: JsonElement? = null,
        val httpStatusCode: Int,
        val httpStatus: String,
        val error: JsonElement,
        val occurredAt: Instant,
    ) : LoopEvent

    @Serializable
    @SerialName("ToolUseRequested")
    data class ToolUseRequested(
        val content: JsonElement,
        val requestedAt: Instant,
    ) : LoopEvent

    @Serializable
    @SerialName("ConversationMessageReceived")
    data class ConversationMessageReceived(
        val messageId: MessageId,
        val convName: CollaboratorName,
        val from: CollaboratorName,
        val body: String,
        val receivedAt: Instant,
    ) : LoopEvent

    /** Moved from [AgentEvent]: LLM request are execution-scoped, not lifecycle-scoped. */
    @Serializable
    @SerialName("LLMRequestSent")
    data class LLMRequestSent(
        val model: String,
        val inputTokens: Int,
        val occurredAt: Instant,
    ) : LoopEvent

    /** Moved from [AgentEvent]: LLM responses are execution-scoped, not lifecycle-scoped. */
    @Serializable
    @SerialName("LLMResponseReceived")
    data class LLMResponseReceived(
        val outputTokens: Int,
        val occurredAt: Instant,
    ) : LoopEvent
}

/**
 * Safely cast an untyped [StreamName] to [StreamName.Loop].
 * Use this in [EventStore.subscribeToAll] handlers after a type-check on `envelope.data`.
 */
fun StreamName<*>.asLoopStream(): StreamName.Loop =
    this as? StreamName.Loop
        ?: error("Expected StreamName.Loop but got ${this::class.simpleName} for path '$path'")
