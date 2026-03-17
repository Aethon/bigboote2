package com.bigboote.coordinator.projections

import com.bigboote.events.eventstore.EventEnvelope

/**
 * Marker interface for event-driven read model projections.
 *
 * Projections subscribe to event streams via the EventStore and maintain
 * denormalised Postgres tables for efficient query access. They maintain
 * an in-memory checkpoint of the last processed event count and resume
 * from position 0 on restart (idempotent upserts guarantee correctness).
 *
 * See Architecture doc Section 8.1.
 */
interface Projection {

    /** Stable name used in logs and diagnostics. */
    val name: String

    /**
     * Stream identifier or pattern this projection subscribes to.
     * Concrete implementations use this to configure catch-up subscriptions.
     */
    val streamPattern: String

    /**
     * Process a single event envelope. Called by the catch-up subscription
     * handler and also directly from route handlers for immediate consistency.
     * Must be idempotent — it may be called more than once for the same event
     * if the subscription replays it after a direct call.
     */
    suspend fun handle(envelope: EventEnvelope)

    /**
     * Returns the count of events processed since the last restart.
     * DECISION: Phase 5 uses in-memory checkpointing only. A durable
     * checkpoint table will be introduced when persistent resume-on-restart
     * is required (not part of Phase 5 verification gate).
     */
    suspend fun checkpoint(): Long

    /**
     * Start the projection: subscribe to known streams and begin processing.
     * Called once by [ProjectionRunner] at coordinator startup.
     */
    suspend fun start()

    /**
     * Stop the projection and release subscriptions.
     */
    fun stop()
}
