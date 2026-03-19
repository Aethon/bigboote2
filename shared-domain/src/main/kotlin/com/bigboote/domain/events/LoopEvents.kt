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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Describes an event in an agent loop.
 *
 * Note that these events do not call out the ID of the agent they apply to,
 * as the agent ID is inferred from the context in which the event is found (i.e., the stream ID).
 */
@Serializable
sealed interface LoopEvent :
    Event {

    /**
     * The agent loop started a step.
     *
     * @property startedAt The time the step started.
     */
    @Serializable
    @SerialName("StepStarted")
    data class StepStarted(
        val startedAt: Instant,
    ) : LoopEvent

    /**
     * An agent loop step ended.
     *
     * @property result The result of the step.
     * @property endedAt The time the step ended.
     */
    @Serializable
    @SerialName("StepEnded")
    data class StepEnded(
        val result: LoopStatus,
        val endedAt: Instant,
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
     * @property satisfiedContentIds The IDs of the content blocks that were satisfied by this message.
     */
    @Serializable
    @SerialName("AssistantTurnSucceeded")
    data class AssistantTurnSucceeded
    @OptIn(ExperimentalUuidApi::class)
    constructor(
        val newMessage: Message? = null,
        val response: MessageResponse,
        val assistantStatus: AssistantStatus,
        val satisfiedContentIds: Set<Uuid> = emptySet()
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
}
