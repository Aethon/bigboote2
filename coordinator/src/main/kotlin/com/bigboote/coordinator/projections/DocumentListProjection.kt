package com.bigboote.coordinator.projections

import com.bigboote.coordinator.projections.db.DocumentTable
import com.bigboote.domain.events.DocumentEvent
import com.bigboote.domain.events.DocumentEvent.*
import com.bigboote.domain.values.DocumentId
import com.bigboote.domain.values.EffortId
import com.bigboote.events.eventstore.EventEnvelope
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.eventstore.EventSubscription
import com.bigboote.events.streams.StreamNames
import com.bigboote.infra.db.dbQuery
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val logger = LoggerFactory.getLogger(DocumentListProjection::class.java)

/**
 * Maintains the `documents` Postgres read model from KurrentDB document events.
 *
 * Two delivery paths:
 * 1. **Direct** (`project(event)`): called synchronously from API routes immediately
 *    after appending events to KurrentDB, ensuring immediate read-your-writes consistency.
 * 2. **Subscription** (`trackEffort(effortId)` / `start()`): catch-up subscriptions
 *    from position 0 providing crash recovery and replay. All DB writes are idempotent
 *    upserts/updates so double-delivery is harmless.
 *
 * One subscription per effort docs stream (`/effort:{id}/docs`). New efforts are
 * tracked when their first document is created via [trackEffort].
 *
 * See Architecture doc Section 14.
 */
class DocumentListProjection(
    private val eventStore: EventStore,
) : Projection {

    override val name          = "document-list"
    override val streamPattern = "/effort:*/docs"

    private val processedCount = AtomicLong(0)
    private val subscriptions  = ConcurrentHashMap<String, EventSubscription>()

    // ------------------------------------------------------------------ lifecycle

    /**
     * Start the projection by subscribing to all effort docs streams already known
     * in the read model. Called once by [ProjectionRunner] at coordinator startup.
     */
    override suspend fun start() {
        // DECISION: selectAll() + distinctBy in memory — avoids deprecated slice() API
        // (removed in Exposed 0.41+). Performance is acceptable at startup given the
        // expected number of efforts.
        val knownEffortIds: List<EffortId> = dbQuery {
            DocumentTable
                .selectAll()
                .map { EffortId(it[DocumentTable.effortId]) }
                .distinctBy { it.value }
        }
        logger.info(
            "DocumentListProjection starting: subscribing to {} known effort doc stream(s)",
            knownEffortIds.size
        )
        knownEffortIds.forEach { subscribeToStream(it) }
    }

    override fun stop() {
        subscriptions.values.forEach { it.stop() }
        subscriptions.clear()
        logger.info("DocumentListProjection stopped")
    }

    // ------------------------------------------------------------------ public API

    /**
     * Begin tracking the docs stream for a new effort via a catch-up subscription
     * from position 0. Idempotent — calling twice for the same effort has no effect.
     * Called by API routes immediately after the first document in an effort is created.
     */
    fun trackEffort(effortId: EffortId) {
        val streamId = StreamNames.docs(effortId)
        if (!subscriptions.containsKey(streamId)) {
            subscribeToStream(effortId)
        }
    }

    /**
     * Synchronously project a single document event into the read model.
     * Called from API routes for immediate read-your-writes consistency.
     * Also called internally by the subscription handler.
     */
    suspend fun project(event: DocumentEvent) {
        when (event) {
            is DocumentCreated -> upsertDocument(event)
            is DocumentUpdated -> updateS3Key(event)
            is DocumentDeleted -> markDeleted(event)
        }
        processedCount.incrementAndGet()
    }

    // ------------------------------------------------------------------ Projection interface

    override suspend fun handle(envelope: EventEnvelope) {
        val event = envelope.data as? DocumentEvent ?: return
        project(event)
    }

    override suspend fun checkpoint(): Long = processedCount.get()

    // ------------------------------------------------------------------ private helpers

    private fun subscribeToStream(effortId: EffortId) {
        val streamId = StreamNames.docs(effortId)
        subscriptions.computeIfAbsent(streamId) {
            eventStore.subscribeToStream(streamId, fromVersion = 0L) { envelope ->
                handle(envelope)
            }.also {
                logger.debug(
                    "DocumentListProjection: started catch-up subscription on '{}'", streamId
                )
            }
        }
    }

    private suspend fun upsertDocument(event: DocumentCreated) {
        dbQuery {
            DocumentTable.upsert {
                it[documentId] = event.documentId.value
                it[effortId]   = event.effortId.value
                it[name]       = event.name
                it[mimeType]   = event.mimeType
                it[s3Key]      = event.s3Key
                it[createdAt]  = event.createdAt
                it[createdBy]  = event.createdBy.toString()
                it[deleted]    = false
            }
        }
        logger.debug(
            "DocumentListProjection: upserted DocumentCreated {} in {}",
            event.documentId, event.effortId
        )
    }

    private suspend fun updateS3Key(event: DocumentUpdated) {
        dbQuery {
            DocumentTable.update(
                where = {
                    (DocumentTable.documentId eq event.documentId.value) and
                    (DocumentTable.effortId eq event.effortId.value)
                }
            ) {
                it[s3Key] = event.s3Key
            }
        }
        logger.debug(
            "DocumentListProjection: updated s3Key for {} in {}",
            event.documentId, event.effortId
        )
    }

    private suspend fun markDeleted(event: DocumentDeleted) {
        dbQuery {
            DocumentTable.update(
                where = {
                    (DocumentTable.documentId eq event.documentId.value) and
                    (DocumentTable.effortId eq event.effortId.value)
                }
            ) {
                it[deleted] = true
            }
        }
        logger.debug(
            "DocumentListProjection: marked deleted {} in {}",
            event.documentId, event.effortId
        )
    }
}
