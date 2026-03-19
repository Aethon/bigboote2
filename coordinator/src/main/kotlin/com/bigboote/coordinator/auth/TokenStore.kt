package com.bigboote.coordinator.auth

import com.bigboote.domain.values.AgentId
import com.bigboote.domain.values.CollaboratorName
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger(TokenStore::class.java)

/**
 * Thread-safe in-memory store mapping agent tokens to their AgentId and
 * CollaboratorName for use by the authentication validators.
 *
 * Two token types are tracked:
 * - X-Gateway-Token: presented by an agent to the Coordinator's Agent Gateway API.
 * - X-Agent-Token:   presented by the Coordinator to an agent's Control API.
 *
 * Additionally, each AgentId can have a CollaboratorName registered for use by
 * AgentGatewayHandler when enforcing per-conversation access control (Phase 12+).
 *
 * Lifecycle:
 * - [register] is called by SpawnReactor (Phase 10) when an AgentSpawnRequested
 *   event is processed, before the container is started.
 * - [deregister] is called by TerminationReactor (Phase 10) on agent shutdown.
 *
 * See Architecture doc Section 13.1 (AuthModule) and Section 7.2 (SpawnReactor).
 */
class TokenStore {

    // X-Gateway-Token → AgentId
    private val gatewayTokens = ConcurrentHashMap<String, AgentId>()

    // X-Agent-Token → AgentId  (used by Agent Control API, Phase 10+)
    private val agentTokens = ConcurrentHashMap<String, AgentId>()

    // AgentId → CollaboratorName  (used by AgentGatewayHandler, Phase 12+)
    private val collaboratorNames = ConcurrentHashMap<AgentId, CollaboratorName>()

    // ------------------------------------------------------------------ write API

    /**
     * Register both tokens for a newly spawned agent. Called by SpawnReactor
     * before the container is started so tokens are available immediately
     * on the first incoming request.
     */
    fun register(agentId: AgentId, gatewayToken: String, agentToken: String) {
        gatewayTokens[gatewayToken] = agentId
        agentTokens[agentToken]     = agentId
        logger.debug("TokenStore: registered tokens for {}", agentId)
    }

    /**
     * Register the collaborator name associated with an agent.
     * Called by SpawnReactor alongside [register] so that AgentGatewayHandler
     * can resolve the sender identity when posting conversation messages.
     */
    fun registerCollaboratorName(agentId: AgentId, name: CollaboratorName) {
        collaboratorNames[agentId] = name
    }

    /**
     * Remove all token and name entries for [agentId] on agent termination.
     * Called by TerminationReactor (Phase 10+).
     */
    fun deregister(agentId: AgentId) {
        gatewayTokens.entries.removeIf { it.value == agentId }
        agentTokens.entries.removeIf   { it.value == agentId }
        collaboratorNames.remove(agentId)
        logger.debug("TokenStore: deregistered tokens for {}", agentId)
    }

    // ------------------------------------------------------------------ read API

    /** Resolve an X-Gateway-Token to its AgentId. Returns null if unknown. */
    fun resolveGatewayToken(token: String): AgentId? = gatewayTokens[token]

    /** Resolve an X-Agent-Token to its AgentId. Returns null if unknown. */
    fun resolveAgentToken(token: String): AgentId? = agentTokens[token]

    /** Look up the CollaboratorName for an agent. Returns null if not registered. */
    fun getCollaboratorName(agentId: AgentId): CollaboratorName? = collaboratorNames[agentId]
}
