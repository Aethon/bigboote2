package com.bigboote.coordinator.proxy.spawn

import com.bigboote.coordinator.proxy.agent.AgentProxy
import com.bigboote.domain.values.AgentId

/**
 * Strategy interface for spawning agent instances.
 *
 * Phase 10 provides [DockerSpawnStrategy] (local Docker daemon).
 * Phase 20 will add FlyMachineSpawnStrategy (Fly.io Machines API).
 *
 * The active strategy is selected at startup via the `spawnStrategy` field on
 * [com.bigboote.domain.aggregates.AgentTypeState] (value: "docker" or "fly").
 * For Phase 10, "docker" is the only supported value.
 *
 * See Architecture doc Section 10.1.
 */
interface SpawnStrategy {

    /**
     * Spawn a new agent container from [config].
     *
     * Implementations must:
     *   1. Start the container with the correct environment variables.
     *   2. Determine the reachable control URL for the running container.
     *   3. Return an [AgentProxy] pointing at that control URL.
     *
     * This method does NOT call /control/v1/start — that is the caller's
     * responsibility after registration in [com.bigboote.coordinator.proxy.ProxyRegistry].
     *
     * @throws RuntimeException if the container fails to start.
     */
    suspend fun spawn(config: SpawnConfig): AgentProxy

    /**
     * Stop and remove the container for [agentId].
     * No-op if the agent was already stopped or is unknown.
     */
    suspend fun teardown(agentId: AgentId)
}
