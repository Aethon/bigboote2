package com.bigboote.coordinator.projections

import com.bigboote.coordinator.projections.db.EffortTable
import com.bigboote.domain.aggregates.EffortStatus
import com.bigboote.domain.events.EffortEvent
import com.bigboote.domain.events.EffortEvent.*
import com.bigboote.domain.events.asEffortStream
import com.bigboote.domain.values.CollaboratorSpec
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.StreamName
import com.bigboote.events.eventstore.EventEnvelope
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.eventstore.EventSubscription
import com.bigboote.infra.db.dbQuery
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val logger = LoggerFactory.getLogger(EffortSummaryProjectionImpl::class.java)

/**
 * Public surface of the effort read model projection consumed by route handlers.
 *
 * Extracted as an interface so that route-layer tests can mock it without
 * needing `open` or a real DB/EventStore. The sole production implementation
 * is [EffortSummaryProjectionImpl].
 *
 * See Architecture doc Section 8.2.
 */
interface EffortSummaryProjection : Projection {
    /** Begin tracking a newly created effort stream (called from route handlers). */
    fun trackEffort(effortId: EffortId)
    /** Synchronously project one event for immediate read-your-writes consistency. */
    suspend fun project(event: EffortEvent, stream: StreamName.Effort)
}

/**
 * Production implementation of [EffortSummaryProjection].
 *
 * Maintains the `efforts` Postgres read model from KurrentDB effort events.
 *
 * Two delivery paths are used for correctness:
 * 1. **Direct** (`project(event, stream)`): called synchronously from API routes
 *    immediately after appending events to KurrentDB, ensuring immediate
 *    read-your-writes consistency within a single request/response cycle.
 * 2. **Subscription** (`trackEffort(id)` / `start()`): catch-up subscriptions from
 *    position 0 on each known effort stream, providing crash recovery and replay
 *    semantics. All DB writes are idempotent upserts so double-delivery is harmless.
 *
 * See Architecture doc Section 8.2.
 */
class EffortSummaryProjectionImpl(
    private val eventStore: EventStore,
) : EffortSummaryProjection {

    override val name = "effort-summary"
    override val streamPattern = "/effort:*"

    private val processedCount = AtomicLong(0)
    private val subscriptions = ConcurrentHashMap<String, EventSubscription>()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Start the projection by subscribing to all effort streams already known
     * in the read model. Called once by [ProjectionRunner] at coordinator startup.
     *
     * DECISION: Phase 5 always replays from position 0 on startup (no durable
     * checkpoint). Upsert semantics guarantee idempotency. A persistent checkpoint
     * table will be introduced in a later phase when re-projection cost becomes
     * prohibitive.
     */
    override suspend fun start() {
        val knownEffortIds = dbQuery {
            EffortTable
                .selectAll()
                .map { EffortId(it[EffortTable.effortId]) }
        }
        logger.info("EffortSummaryProjection starting: subscribing to {} known effort stream(s)", knownEffortIds.size)
        knownEffortIds.forEach { effortId -> subscribeToStream(effortId) }
    }

    override fun stop() {
        subscriptions.values.forEach { it.stop() }
        subscriptions.clear()
        logger.info("EffortSummaryProjection stopped")
    }

    // ------------------------------------------------------------------ public API

    /**
     * Begin tracking a new effort stream via a catch-up subscription from position 0.
     * Idempotent: calling twice for the same effort has no effect.
     * Called by API routes immediately after a new Effort is created.
     */
    override fun trackEffort(effortId: EffortId) {
        val streamPath = StreamName.Effort(effortId).path
        if (!subscriptions.containsKey(streamPath)) {
            subscribeToStream(effortId)
        }
    }

    /**
     * Synchronously project a single event into the read model.
     * Called from API routes for immediate read-your-writes consistency.
     * Also called internally by the subscription handler.
     *
     * [stream] provides the [EffortId] context that is no longer carried in
     * event payloads.
     */
    override suspend fun project(event: EffortEvent, stream: StreamName.Effort) {
        when (event) {
            is EffortCreated -> upsertCreated(event, stream.id)
            is EffortStarted -> updateStatus(stream.id, EffortStatus.ACTIVE, startedAt = event.occurredAt)
            is EffortPaused -> updateStatus(stream.id, EffortStatus.PAUSED)
            is EffortResumed -> updateStatus(stream.id, EffortStatus.ACTIVE)
            is EffortClosed -> updateStatus(stream.id, EffortStatus.CLOSED, closedAt = event.occurredAt)
            is AgentSpawnRequested -> { /* no-op: agent instances tracked separately in later phases */ }
        }
        processedCount.incrementAndGet()
    }

    // ------------------------------------------------------------------ Projection interface

    /**
     * Handle an event envelope from the catch-up subscription.
     * Delegates to [project] after extracting the EffortEvent and stream.
     */
    override suspend fun handle(envelope: EventEnvelope<Any>) {
        val event = envelope.data as? EffortEvent ?: return
        val stream = envelope.streamName.asEffortStream()
        project(event, stream)
    }

    override suspend fun checkpoint(): Long = processedCount.get()

    // ------------------------------------------------------------------ private helpers

    private fun subscribeToStream(effortId: EffortId) {
        val streamName = StreamName.Effort(effortId)
        subscriptions.computeIfAbsent(streamName.path) {
            eventStore.subscribeToStream(streamName, fromVersion = 0L) { envelope ->
                handle(envelope)
            }.also {
                logger.debug("EffortSummaryProjection: started catch-up subscription on '{}'", streamName.path)
            }
        }
    }

    private suspend fun upsertCreated(event: EffortCreated, effortId: EffortId) {
        val collaboratorsJson = encodeCollaborators(event.collaborators)
        dbQuery {
            EffortTable.upsert {
                it[EffortTable.effortId] = effortId.value
                it[name] = event.name
                it[goal] = event.goal
                it[status] = EffortStatus.CREATED
                it[leadName] = event.leadName.toString()
                it[collaborators] = collaboratorsJson
                it[createdAt] = event.createdAt
            }
        }
        logger.debug("EffortSummaryProjection: upserted EffortCreated for {}", effortId)
    }

    private suspend fun updateStatus(
        effortId: EffortId,
        newStatus: EffortStatus,
        startedAt: Instant? = null,
        closedAt: Instant? = null,
    ) {
        dbQuery {
            EffortTable.update({ EffortTable.effortId eq effortId.value }) { row ->
                row[status] = newStatus
                if (startedAt != null) row[EffortTable.startedAt] = startedAt
                if (closedAt != null) row[EffortTable.closedAt] = closedAt
            }
        }
        logger.debug("EffortSummaryProjection: updated status to {} for {}", newStatus, effortId)
    }

    private fun encodeCollaborators(specs: List<CollaboratorSpec>): String =
        json.encodeToString(specs)
}
