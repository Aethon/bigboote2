package com.bigboote.coordinator.reactors

import com.bigboote.coordinator.proxy.ProxyRegistry
import com.bigboote.coordinator.proxy.spawn.SpawnConfig
import com.bigboote.coordinator.proxy.spawn.SpawnStrategy
import com.bigboote.coordinator.projections.repositories.AgentTypeReadRepository
import com.bigboote.domain.events.EffortEvent.AgentSpawnRequested
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.eventstore.EventSubscription
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(SpawnReactor::class.java)

/**
 * Reacts to [AgentSpawnRequested] events from KurrentDB and spawns agent containers.
 *
 * Uses a persistent subscription on `\$all` so that spawn events are processed
 * at-least-once with durable acknowledgement across coordinator restarts.
 *
 * **Processing flow:**
 * 1. Receive [AgentSpawnRequested] via persistent subscription.
 * 2. Look up the AgentType read model to retrieve the Docker image.
 * 3. Build [SpawnConfig] from the event and read model.
 * 4. Call [SpawnStrategy.spawn] to start the container and obtain an [AgentProxy][com.bigboote.coordinator.proxy.agent.AgentProxy].
 * 5. Register the proxy in [ProxyRegistry].
 * 6. Call [AgentProxy.start][com.bigboote.coordinator.proxy.agent.AgentProxy.start] to send the agent its Effort context and Gateway URL.
 *
 * Non-[AgentSpawnRequested] events from `\$all` are silently ignored.
 * Spawn failures are logged but do not crash the reactor.
 *
 * [coordinatorGatewayUrl] is the base URL of the coordinator's internal (gateway) API
 * that spawned agents must use to communicate back, e.g.
 * `http://host.docker.internal:8080/internal/v1`. Read from the
 * `BIGBOOTE_COORDINATOR_GATEWAY_URL` environment variable (see Application.kt).
 *
 * See Architecture doc Section 10.
 */
class SpawnReactor(
    private val eventStore: EventStore,
    private val agentTypeReadRepository: AgentTypeReadRepository,
    private val spawnStrategy: SpawnStrategy,
    private val proxyRegistry: ProxyRegistry,
    private val coordinatorGatewayUrl: String,
) {
    private var subscription: EventSubscription? = null

    /**
     * Start the persistent subscription on `\$all`.
     * Called once by [ReactorRunner] at coordinator startup.
     */
    fun start() {
        subscription = eventStore.subscribePersistent(
            streamId  = "\$all",
            groupName = "spawn-reactor",
        ) { envelope ->
            val event = envelope.data
            if (event is AgentSpawnRequested) {
                handleSpawnRequested(event)
            }
        }
        logger.info("SpawnReactor started (persistent subscription on \$all, group=spawn-reactor)")
    }

    /**
     * Stop the persistent subscription.
     * Called by [ReactorRunner] on coordinator shutdown.
     */
    fun stop() {
        subscription?.stop()
        subscription = null
        logger.info("SpawnReactor stopped")
    }

    // ---- private helpers ----

    private suspend fun handleSpawnRequested(event: AgentSpawnRequested) {
        logger.info(
            "SpawnReactor: AgentSpawnRequested for agent {} ({}) in effort {}",
            event.agentId, event.collaboratorName, event.effortId,
        )

        // Look up AgentType to get dockerImage (read from Postgres read model)
        val agentType = agentTypeReadRepository.get(event.agentTypeId)
        if (agentType == null) {
            logger.error(
                "SpawnReactor: AgentType {} not found in read model — cannot spawn agent {}",
                event.agentTypeId, event.agentId,
            )
            return
        }

        val config = SpawnConfig(
            agentId          = event.agentId,
            effortId         = event.effortId,
            agentTypeId      = event.agentTypeId,
            collaboratorName = event.collaboratorName,
            gatewayToken     = event.gatewayToken,
            agentToken       = event.agentToken,
            dockerImage      = agentType.dockerImage,
        )

        try {
            val proxy = spawnStrategy.spawn(config)
            proxyRegistry.register(event.effortId, event.collaboratorName, proxy)
            val startResponse = proxy.start(event.effortId, event.agentTypeId, coordinatorGatewayUrl)
            logger.info(
                "SpawnReactor: agent {} started successfully (started={})",
                event.agentId, startResponse.started,
            )
        } catch (e: Exception) {
            logger.error(
                "SpawnReactor: failed to spawn or start agent {}: {}",
                event.agentId, e.message, e,
            )
        }
    }
}
