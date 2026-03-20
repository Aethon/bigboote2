package com.bigboote.coordinator.proxy.agent

import com.bigboote.coordinator.proxy.CollaboratorProxy
import com.bigboote.domain.values.AgentId
import com.bigboote.domain.values.AgentTypeId
import com.bigboote.domain.values.EffortId

/**
 * Abstraction over a running agent instance's Control API.
 *
 * Extends [CollaboratorProxy] so that [com.bigboote.coordinator.reactors.MessageDeliveryReactor]
 * can deliver messages to agents alongside human (ExternalProxy) collaborators.
 *
 * Implementations send HTTP requests to the agent's /control/v1/ endpoints using
 * the per-instance agent token for authentication.
 *
 * Used by [com.bigboote.coordinator.reactors.SpawnReactor] after container start,
 * and by Phase 15's EffortLifecycleReactor for pause/resume/stop signals.
 *
 * Message delivery ([deliverChannelMessage], [deliverDirectMessage]) is handled via
 * the coordinator's gateway SSE subscription endpoint for agents
 * (`/internal/v1/agent/{id}/subscribe-conversation-events`). Phase 13 provides
 * log-only stubs.
 *
 * See Architecture doc Sections 9.1, 10.2.
 */
interface AgentProxy : CollaboratorProxy {
    /** Identity of the agent instance this proxy controls. */
    val agentId: AgentId

    /** Base URL of the agent's Control API, e.g. "http://127.0.0.1:49153/control/v1". */
    val controlUrl: String

    /**
     * Send the start command to the agent, providing its Effort context and the
     * coordinator's Gateway API URL.
     */
    suspend fun start(
        effortId: EffortId,
        agentTypeId: AgentTypeId,
        agentGatewayUrl: String,
    ): AgentStartResponse

    /** Query the agent's current loop state. */
    suspend fun status(): AgentStatusResponse

    /** Signal the agent to pause its loop at the next safe point. */
    suspend fun pause(): AgentAckResponse

    /** Signal the agent to resume after a pause. */
    suspend fun resume(): AgentAckResponse

    /** Signal the agent to stop and shut down gracefully. */
    suspend fun stop(): AgentAckResponse
}

// ---- Local mirror DTOs for the agent Control API ----
// The agent-service module is not a dependency of the coordinator; these mirrors
// replicate only the fields the coordinator cares about, keyed on the same JSON
// field names. See agent-service AgentControlDtos.kt for the canonical definition.

data class AgentStartResponse(val started: Boolean, val instanceId: AgentId)

data class AgentStatusResponse(
    val instanceId: AgentId,
    val effortId: EffortId,
    val agentTypeId: AgentTypeId,
    val loopState: String,
    val currentTurn: Int,
    val lastActivityAt: String?,
    val streamPosition: Long,
)

data class AgentAckResponse(val success: Boolean, val instanceId: AgentId)
