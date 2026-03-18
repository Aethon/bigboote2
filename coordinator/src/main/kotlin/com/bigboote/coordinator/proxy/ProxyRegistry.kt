package com.bigboote.coordinator.proxy

import com.bigboote.coordinator.proxy.agent.AgentProxy
import com.bigboote.domain.values.AgentId
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.EffortId
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger(ProxyRegistry::class.java)

/**
 * In-memory registry mapping (effortId, collaboratorName) → [AgentProxy].
 *
 * Explicitly permitted to be mutable in-memory per the Architecture doc (Section 10.3):
 * the coordinator process is single-leader and the registry is rebuilt on restart
 * via the SpawnReactor replaying events from KurrentDB.
 *
 * Thread-safe: backed by nested [ConcurrentHashMap]s. The outer map is keyed by
 * effortId; the inner map is keyed by the string representation of collaboratorName.
 *
 * See Architecture doc Section 10.3.
 */
class ProxyRegistry {

    // effortId.value → (collaboratorName.toString() → AgentProxy)
    private val registry = ConcurrentHashMap<String, ConcurrentHashMap<String, AgentProxy>>()

    /**
     * Register an [AgentProxy] for an agent in an effort.
     * Replaces any existing proxy for the same (effortId, collaboratorName) pair.
     */
    fun register(effortId: EffortId, collaboratorName: CollaboratorName, proxy: AgentProxy) {
        registry
            .computeIfAbsent(effortId.value) { ConcurrentHashMap() }
            .put(collaboratorName.toString(), proxy)
        logger.info(
            "ProxyRegistry: registered agent {} ({}) for effort {}",
            proxy.agentId, collaboratorName, effortId,
        )
    }

    /**
     * Look up the proxy for a specific agent in an effort.
     * Returns null if not found.
     */
    fun get(effortId: EffortId, collaboratorName: CollaboratorName): AgentProxy? =
        registry[effortId.value]?.get(collaboratorName.toString())

    /**
     * Look up a proxy by [AgentId] across all efforts.
     * Scans all effort buckets; O(n) in total agents.
     */
    fun getById(agentId: AgentId): AgentProxy? =
        registry.values
            .flatMap { it.values }
            .firstOrNull { it.agentId == agentId }

    /**
     * All proxies registered for a given effort. Empty if none.
     */
    fun getAll(effortId: EffortId): Collection<AgentProxy> =
        registry[effortId.value]?.values ?: emptyList()

    /**
     * Remove a proxy registration. No-op if not found.
     */
    fun unregister(effortId: EffortId, collaboratorName: CollaboratorName) {
        val removed = registry[effortId.value]?.remove(collaboratorName.toString())
        if (removed != null) {
            logger.info(
                "ProxyRegistry: unregistered agent {} ({}) from effort {}",
                removed.agentId, collaboratorName, effortId,
            )
        }
    }
}
