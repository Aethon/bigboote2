package com.bigboote.domain.events

import com.bigboote.domain.values.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
sealed interface AgentEvent {
    val agentId: AgentId

    @Serializable
    @SerialName("AgentStarted")
    data class AgentStarted(
        override val agentId: AgentId,
        val effortId: EffortId,
        val agentTypeId: AgentTypeId,
        val collaboratorName: CollaboratorName,
        val supportedGatewayApiVersions: List<String>,
        val supportedControlApiVersions: List<String>,
        val startedAt: Instant,
    ) : AgentEvent

    @Serializable
    @SerialName("AgentStopped")
    data class AgentStopped(
        override val agentId: AgentId,
        val occurredAt: Instant,
    ) : AgentEvent

    @Serializable
    @SerialName("AgentFailed")
    data class AgentFailed(
        override val agentId: AgentId,
        val reason: String,
        val failedAt: Instant,
    ) : AgentEvent

    @Serializable
    @SerialName("AgentPaused")
    data class AgentPaused(
        override val agentId: AgentId,
        val occurredAt: Instant,
    ) : AgentEvent

    @Serializable
    @SerialName("AgentResumed")
    data class AgentResumed(
        override val agentId: AgentId,
        val occurredAt: Instant,
    ) : AgentEvent

    @Serializable
    @SerialName("LLMRequestSent")
    data class LLMRequestSent(
        override val agentId: AgentId,
        val model: String,
        val inputTokens: Int,
        val occurredAt: Instant,
    ) : AgentEvent

    @Serializable
    @SerialName("LLMResponseReceived")
    data class LLMResponseReceived(
        override val agentId: AgentId,
        val outputTokens: Int,
        val occurredAt: Instant,
    ) : AgentEvent

    // DECISION: ToolInvoked and ToolResultReceived are from Architecture doc v1.2;
    // not yet in Event Schema v1.1. Included here per architecture package structure.

    @Serializable
    @SerialName("ToolInvoked")
    data class ToolInvoked(
        override val agentId: AgentId,
        val toolName: String,
        val toolCallId: String,
        val parameters: JsonObject,
        val invokedAt: Instant,
    ) : AgentEvent

    @Serializable
    @SerialName("ToolResultReceived")
    data class ToolResultReceived(
        override val agentId: AgentId,
        val toolCallId: String,
        val result: String,
        val isError: Boolean,
        val receivedAt: Instant,
    ) : AgentEvent
}
