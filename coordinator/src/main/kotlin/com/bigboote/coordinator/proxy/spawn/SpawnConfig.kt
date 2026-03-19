package com.bigboote.coordinator.proxy.spawn

import com.bigboote.domain.values.AgentId
import com.bigboote.domain.values.AgentTypeId
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.EffortId

/**
 * All parameters needed to spawn a new agent instance.
 *
 * Constructed by [com.bigboote.coordinator.reactors.SpawnReactor] from the
 * [com.bigboote.domain.events.EffortEvents.AgentSpawnRequested] event, augmented
 * with [dockerImage] looked up from the [com.bigboote.coordinator.projections.repositories.AgentTypeReadRepository].
 *
 * See Architecture doc Section 10.1.
 */
data class SpawnConfig(
    val agentId: AgentId,
    val effortId: EffortId,
    val agentTypeId: AgentTypeId,
    val collaboratorName: CollaboratorName,
    /** Per-instance token the agent uses to authenticate against the Gateway API (X-Gateway-Token). */
    val gatewayToken: String,
    /** Per-instance token the coordinator uses to authenticate against the agent's Control API (X-Agent-Token). */
    val agentToken: String,
    /** Docker image tag to launch, e.g. "bigboote/agent-service:latest". */
    val dockerImage: String,
)
