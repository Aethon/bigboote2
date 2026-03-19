package com.bigboote.coordinator.reactors

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ReactorRunner::class.java)

/**
 * Manages the lifecycle of all Reactors for the Coordinator.
 *
 * Starts each reactor on coordinator startup by calling its [start] method,
 * which establishes persistent subscriptions to KurrentDB. On shutdown, [stop]
 * cancels all subscriptions.
 *
 * Phase 10: [SpawnReactor]
 * Phase 13: [MessageDeliveryReactor]
 * Phase 15: [SystemMessageReactor], [EffortLifecycleReactor]
 *
 * See Architecture doc Section 10.
 */
class ReactorRunner(
    private val spawnReactor: SpawnReactor,
    private val messageDeliveryReactor: MessageDeliveryReactor,
    private val systemMessageReactor: SystemMessageReactor,       // Phase 15
    private val effortLifecycleReactor: EffortLifecycleReactor,  // Phase 15
) {

    /**
     * Start all reactors. Called once by [com.bigboote.coordinator.Application] after
     * the projection runner has started (ensuring the read model is populated
     * before reactors try to look up AgentType data).
     */
    fun start() {
        logger.info("ReactorRunner starting reactors...")
        spawnReactor.start()
        messageDeliveryReactor.start()
        systemMessageReactor.start()
        effortLifecycleReactor.start()
        logger.info("ReactorRunner: all reactors started")
    }

    /**
     * Stop all reactors and release subscriptions.
     * Called during coordinator shutdown.
     */
    fun stop() {
        spawnReactor.stop()
        messageDeliveryReactor.stop()
        systemMessageReactor.stop()
        effortLifecycleReactor.stop()
        logger.info("ReactorRunner: all reactors stopped")
    }
}
