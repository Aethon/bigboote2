package com.bigboote.domain.events

import com.bigboote.domain.aggregates.AssistantStatus
import com.bigboote.domain.aggregates.LoopStatus
import com.bigboote.domain.values.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
sealed interface LoopEvent {
    val agentId: AgentId

    @Serializable
    @SerialName("StepStarted")
    data class StepStarted(
        override val agentId: AgentId,
        val startedAt: Instant,
    ) : LoopEvent

    @Serializable
    @SerialName("StepEnded")
    data class StepEnded(
        override val agentId: AgentId,
        val result: LoopStatus,
        val endedAt: Instant,
    ) : LoopEvent

    @Serializable
    @SerialName("AssistantTurnSucceeded")
    data class AssistantTurnSucceeded(
        override val agentId: AgentId,
        val newMessage: JsonElement? = null,
        val response: JsonElement,
        val assistantStatus: AssistantStatus,
        val satisfiedContentIds: List<String> = emptyList(),
        val occurredAt: Instant,
    ) : LoopEvent

    @Serializable
    @SerialName("AssistantTurnFailed")
    data class AssistantTurnFailed(
        override val agentId: AgentId,
        val newMessage: JsonElement? = null,
        val httpStatusCode: Int,
        val httpStatus: String,
        val error: JsonElement,
        val occurredAt: Instant,
    ) : LoopEvent

    @Serializable
    @SerialName("ToolUseRequested")
    data class ToolUseRequested(
        override val agentId: AgentId,
        val content: JsonElement,
        val requestedAt: Instant,
    ) : LoopEvent

    @Serializable
    @SerialName("ConversationMessageReceived")
    data class ConversationMessageReceived(
        override val agentId: AgentId,
        val messageId: MessageId,
        val convName: CollaboratorName,
        val from: CollaboratorName,
        val body: String,
        val receivedAt: Instant,
    ) : LoopEvent
}
