package com.bigboote.coordinator.reactors

import com.bigboote.coordinator.proxy.ProxyRegistry
import com.bigboote.coordinator.proxy.agent.AgentProxy
import com.bigboote.domain.events.EffortEvent
import com.bigboote.domain.events.EffortEvent.*
import com.bigboote.domain.values.EffortId
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.eventstore.EventSubscription
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(EffortLifecycleReactor::class.java)

/**
 * Reacts to Effort lifecycle events and forwards control signals to all registered
 * [AgentProxy] instances for the affected effort.
 *
 * Uses a persistent subscription on `\$all` (group: `effort-lifecycle-reactor`).
 * On each lifecycle event the reactor looks up all [AgentProxy] entries in
 * [ProxyRegistry] for the effort and calls the corresponding control method:
 *
 * - [EffortPaused]  → [AgentProxy.pause] on each agent
 * - [EffortResumed] → [AgentProxy.resume] on each agent
 * - [EffortClosed]  → [AgentProxy.stop] on each agent
 *
 * [EffortStarted] is intentionally ignored — agents are started individually by
 * [SpawnReactor] after [AgentSpawnRequested] events fire.
 *
 * Proxy call failures are logged as warnings and do not crash the reactor.
 * Agents that are not yet registered (no proxy in registry) are silently skipped.
 *
 * See Architecture doc Section 15.
 */
class EffortLifecycleReactor(
    private val eventStore: EventStore,
    private val proxyRegistry: ProxyRegistry,
) {
    private var subscription: EventSubscription? = null

    /**
     * Start the persistent subscription on `\$all`.
     * Called by [ReactorRunner] at coordinator startup.
     */
    fun start() {
        subscription = eventStore.subscribePersistent(
            streamId  = "\$all",
            groupName = "effort-lifecycle-reactor",
        ) { envelope ->
            when (val event = envelope.data) {
                is EffortPaused  -> signalAgents(event.effortId, Signal.PAUSE)
                is EffortResumed -> signalAgents(event.effortId, Signal.RESUME)
                is EffortClosed  -> signalAgents(event.effortId, Signal.STOP)
                else             -> Unit  // EffortStarted and others are ignored
            }
        }
        logger.info(
            "EffortLifecycleReactor started (persistent subscription on \$all, group=effort-lifecycle-reactor)"
        )
    }

    /**
     * Stop the persistent subscription.
     * Called by [ReactorRunner] on coordinator shutdown.
     */
    fun stop() {
        subscription?.stop()
        subscription = null
        logger.info("EffortLifecycleReactor stopped")
    }

    // ---- private helpers ----

    private enum class Signal { PAUSE, RESUME, STOP }

    private suspend fun signalAgents(effortId: EffortId, signal: Signal) {
        val agents: List<AgentProxy> = proxyRegistry.getAgentProxies(effortId)

        if (agents.isEmpty()) {
            logger.debug(
                "EffortLifecycleReactor: no agents registered for effort {} — skipping {} signal",
                effortId, signal,
            )
            return
        }

        logger.info(
            "EffortLifecycleReactor: sending {} signal to {} agent(s) in effort {}",
            signal, agents.size, effortId,
        )

        for (agent in agents) {
            try {
                when (signal) {
                    Signal.PAUSE  -> {
                        val ack = agent.pause()
                        logger.debug(
                            "EffortLifecycleReactor: agent {} paused (success={})",
                            agent.agentId, ack.success,
                        )
                    }
                    Signal.RESUME -> {
                        val ack = agent.resume()
                        logger.debug(
                            "EffortLifecycleReactor: agent {} resumed (success={})",
                            agent.agentId, ack.success,
                        )
                    }
                    Signal.STOP   -> {
                        val ack = agent.stop()
                        logger.debug(
                            "EffortLifecycleReactor: agent {} stopped (success={})",
                            agent.agentId, ack.success,
                        )
                    }
                }
            } catch (e: Exception) {
                logger.warn(
                    "EffortLifecycleReactor: failed to send {} signal to agent {} in effort {}: {}",
                    signal, agent.agentId, effortId, e.message,
                )
            }
        }
    }
}
