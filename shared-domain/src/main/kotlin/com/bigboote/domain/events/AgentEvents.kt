package com.bigboote.domain.events

import com.bigboote.domain.values.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Events for the Agent lifecycle stream: `/effort:{id}/agent:{id}`
 *
 * Both [AgentId] and [EffortId] are inherent to [StreamName.Agent] and are no longer
 * duplicated in event payloads. Retrieve them via [StreamName.Agent.agentId] and
 * [StreamName.Agent.effortId] from [com.bigboote.events.eventstore.EventEnvelope.streamName].
 *
 * [LLMRequestSent] and [LLMResponseReceived] have moved to [LoopEvent] — they are
 * execution-scoped events that belong on the Loop stream (`/effort:{id}/agent:{id}/loop`).
 *
 * See Architecture doc Change Document v1.0 Section 5.2 / 5.4.
 */
@Serializable
sealed interface AgentEvent {

    @Serializable
    @SerialName("AgentStarted")
    data class AgentStarted(
        val agentTypeId: AgentTypeId,
        val collaboratorName: CollaboratorName,
        val supportedGatewayApiVersions: List<String>,
        val supportedControlApiVersions: List<String>,
        val startedAt: Instant,
    ) : AgentEvent

    @Serializable
    @SerialName("AgentStopped")
    data class AgentStopped(
        val occurredAt: Instant,
    ) : AgentEvent

    @Serializable
    @SerialName("AgentFailed")
    data class AgentFailed(
        val reason: String,
        val failedAt: Instant,
    ) : AgentEvent

    @Serializable
    @SerialName("AgentPaused")
    data class AgentPaused(
        val occurredAt: Instant,
    ) : AgentEvent

    @Serializable
    @SerialName("AgentResumed")
    data class AgentResumed(
        val occurredAt: Instant,
    ) : AgentEvent

    // DECISION: ToolInvoked and ToolResultReceived are from Architecture doc v1.2;
    // not yet in Event Schema v1.1. Included here per architecture package structure.

    @Serializable
    @SerialName("ToolInvoked")
    data class ToolInvoked(
        val toolName: String,
        val toolCallId: String,
        val parameters: JsonObject,
        val invokedAt: Instant,
    ) : AgentEvent

    @Serializable
    @SerialName("ToolResultReceived")
    data class ToolResultReceived(
        val toolCallId: String,
        val result: String,
        val isError: Boolean,
        val receivedAt: Instant,
    ) : AgentEvent
}

/**
 * Safely cast an untyped [StreamName] to [StreamName.Agent].
 * Use this in [EventStore.subscribeToAll] handlers after a type-check on `envelope.data`.
 */
fun StreamName<*>.asAgentStream(): StreamName.Agent =
    this as? StreamName.Agent
        ?: error("Expected StreamName.Agent but got ${this::class.simpleName} for path '$path'")
