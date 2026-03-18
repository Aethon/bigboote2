package com.bigboote.coordinator.projections

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ProjectionRunner::class.java)

/**
 * Manages the lifecycle of all Projections for the Coordinator.
 *
 * Starts each projection on coordinator startup by calling [Projection.start],
 * which subscribes to their respective event streams. On shutdown, [stop] cancels
 * all active subscriptions.
 *
 * Phase 5:  [EffortSummaryProjection]
 * Phase 6:  [AgentTypeSummaryProjection]
 * Phase 11: [ConversationProjection]
 * Phase 14+: DocumentListProjection (add to constructor then)
 *
 * See Architecture doc Section 8.
 */
class ProjectionRunner(
    private val effortSummaryProjection: EffortSummaryProjection,
    private val agentTypeSummaryProjection: AgentTypeSummaryProjection,
    private val conversationProjection: ConversationProjection,   // Phase 11
    // Phase 14+: private val documentListProjection: Projection,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Start all projections in a background coroutine so startup is non-blocking.
     * Each projection's [Projection.start] reads the DB to discover known streams
     * and then starts catch-up subscriptions.
     */
    fun start() {
        scope.launch {
            logger.info("ProjectionRunner starting projections...")
            try {
                effortSummaryProjection.start()
                agentTypeSummaryProjection.start()
                conversationProjection.start()
                logger.info("ProjectionRunner: all projections started")
            } catch (e: Exception) {
                logger.error("ProjectionRunner: error starting projections", e)
            }
        }
    }

    /**
     * Stop all projections and release subscriptions.
     * Called during coordinator shutdown.
     */
    fun stop() {
        effortSummaryProjection.stop()
        agentTypeSummaryProjection.stop()
        conversationProjection.stop()
        logger.info("ProjectionRunner: all projections stopped")
    }
}
