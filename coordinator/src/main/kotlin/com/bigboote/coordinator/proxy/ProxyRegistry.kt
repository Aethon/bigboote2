package com.bigboote.coordinator.proxy

import com.bigboote.coordinator.proxy.agent.AgentProxy
import com.bigboote.domain.values.AgentId
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.EffortId
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger(ProxyRegistry::class.java)

/**
 * In-memory registry mapping (effortId, collaboratorName) → [CollaboratorProxy].
 *
 * Holds proxies for all participant types: [AgentProxy] (spawned agents) and
 * [ExternalProxy] (WebSocket-connected human users). Both are registered here so
 * that [com.bigboote.coordinator.reactors.MessageDeliveryReactor] can fan out
 * message delivery to all members through a single lookup.
 *
 * Explicitly permitted to be mutable in-memory per the Architecture doc (Section 10.3):
 * the coordinator process is single-leader and the registry is rebuilt on restart
 * via the SpawnReactor replaying events from KurrentDB. ExternalProxy entries are
 * ephemeral and not rebuilt — they are re-registered when users reconnect.
 *
 * Thread-safe: backed by nested [ConcurrentHashMap]s. The outer map is keyed by
 * effortId; the inner map is keyed by the string representation of collaboratorName.
 *
 * See Architecture doc Section 9.3.
 */
class ProxyRegistry {

    // effortId.value → (collaboratorName.toString() → CollaboratorProxy)
    private val registry = ConcurrentHashMap<String, ConcurrentHashMap<String, CollaboratorProxy>>()

    /**
     * Register a [CollaboratorProxy] for a collaborator in an effort.
     * Replaces any existing proxy for the same (effortId, collaboratorName) pair.
     */
    fun register(effortId: EffortId, collaboratorName: CollaboratorName, proxy: CollaboratorProxy) {
        registry
            .computeIfAbsent(effortId.value) { ConcurrentHashMap() }
            .put(collaboratorName.toString(), proxy)
        logger.info(
            "ProxyRegistry: registered {} ({}) for effort {}",
            proxy::class.simpleName, collaboratorName, effortId,
        )
    }

    /**
     * Look up the proxy for a collaborator in an effort. Returns null if not found.
     */
    fun get(effortId: EffortId, collaboratorName: CollaboratorName): CollaboratorProxy? =
        registry[effortId.value]?.get(collaboratorName.toString())

    /**
     * Look up an [AgentProxy] by [AgentId] across all efforts.
     * Scans all effort buckets; O(n) in total proxies.
     */
    fun getById(agentId: AgentId): AgentProxy? =
        registry.values
            .flatMap { it.values }
            .filterIsInstance<AgentProxy>()
            .firstOrNull { it.agentId == agentId }

    /**
     * All proxies registered for a given effort. Empty if none.
     */
    fun getAll(effortId: EffortId): Collection<CollaboratorProxy> =
        registry[effortId.value]?.values ?: emptyList()

    /**
     * All [AgentProxy] instances registered for a given effort. Empty if none.
     */
    fun getAgentProxies(effortId: EffortId): List<AgentProxy> =
        registry[effortId.value]?.values?.filterIsInstance<AgentProxy>() ?: emptyList()

    /**
     * Remove a proxy registration. No-op if not found.
     */
    fun unregister(effortId: EffortId, collaboratorName: CollaboratorName) {
        val removed = registry[effortId.value]?.remove(collaboratorName.toString())
        if (removed != null) {
            logger.info(
                "ProxyRegistry: unregistered {} ({}) from effort {}",
                removed::class.simpleName, collaboratorName, effortId,
            )
        }
    }
}
