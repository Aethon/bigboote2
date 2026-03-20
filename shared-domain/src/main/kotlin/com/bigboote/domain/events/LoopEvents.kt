package com.bigboote.domain.events

import com.bigboote.domain.aggregates.AssistantStatus
import com.bigboote.domain.aggregates.LoopStatus
import com.bigboote.domain.values.*
import com.xemantic.ai.anthropic.content.Content
import com.xemantic.ai.anthropic.error.ErrorResponse
import com.xemantic.ai.anthropic.message.Message
import com.xemantic.ai.anthropic.message.MessageResponse
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Describes an event in an agent loop.
 *
 * Note that these events do not call out the ID of the agent they apply to,
 * as the agent ID is inferred from the context in which the event is found (i.e., the stream ID).
 */
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
sealed interface LoopEvent :
    Event {

    /**
     * The agent loop started a step.
     */
    @Serializable
    @SerialName("StepStarted")
    data object StepStarted
        : LoopEvent

    /**
     * An agent loop step ended.
     *
     * @property result The result of the step.
     */
    @Serializable
    @SerialName("StepEnded")
    data class StepEnded(
        val result: LoopStatus
    ) : LoopEvent

    /**
     * The loop successfully sent a message to the assistant, and a valid response was received.
     *
     * NOTE: the assistant may be given a turn without a new message to allow it
     * to resume after a pause response (max_tokens or pause_turn).
     *
     * @property newMessage The message that was added to the assistant context, if any.
     * @property response The response received from the assistant.
     * @property assistantStatus The status of the assistant after the turn.
     * @property lastSentMessageId The last new message ID sent, if any;
     * this and all previous messages have been sent to the assistant.
     */
    @Serializable
    @SerialName("AssistantTurnSucceeded")
    data class AssistantTurnSucceeded(
        val newMessage: Message? = null,
        val response: MessageResponse,
        val assistantStatus: AssistantStatus,
        val lastSentMessageId: MessageId?
    ) : LoopEvent

    /**
     * The loop sent a message to the assistant, and the assistant responded with a recognizable error.
     *
     * @property newMessage The message that was added to the assistant context, if any.
     * @property httpStatusCode The HTTP status code of the error response.
     * @property httpStatus The HTTP status of the error response.
     * @property error Details of the error.
     */
    @Serializable
    @SerialName("AssistantTurnFailed")
    data class AssistantTurnFailed(
        val newMessage: Message? = null,
        val httpStatusCode: Int,
        val httpStatus: String,
        val error: ErrorResponse
    ) : LoopEvent

    /**
     * The assistant requested to use a tool.
     *
     * @property content The tool request content blocks.
     */
    @Serializable
    @SerialName("ToolUseRequested")
    data class ToolUseRequested(
        val content: List<Content>
    ) : LoopEvent

    /**
     * A conversation message was queued for the assistant.
     *
     * @property messageId The ID of the message.
     * @property convName The name of the conversation.
     * @property from The name of the sender.
     * @property content The message content.
     */
    @Serializable
    @SerialName("ConversationMessageReceived")
    data class ConversationMessageReceived(
        val messageId: MessageId,
        val convName: CollaboratorName,
        val from: CollaboratorName,
        val content: List<JsonElement>
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
