package com.bigboote.coordinator.projections

import com.bigboote.coordinator.projections.db.AgentTypeTable
import com.bigboote.domain.events.AgentTypeEvent
import com.bigboote.domain.events.AgentTypeEvent.AgentTypeCreated
import com.bigboote.domain.events.AgentTypeEvent.AgentTypeUpdated
import com.bigboote.domain.values.AgentTypeId
import com.bigboote.events.eventstore.EventEnvelope
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.eventstore.EventSubscription
import com.bigboote.events.streams.StreamNames
import com.bigboote.infra.db.dbQuery
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val logger = LoggerFactory.getLogger(AgentTypeSummaryProjectionImpl::class.java)

/**
 * Contract for the AgentType summary projection, extending the base [Projection]
 * lifecycle contract with the [trackAgentType] read-your-writes hook.
 * Extracted as an interface so route-layer tests can mock it.
 */
interface AgentTypeSummaryProjection : Projection {
    /**
     * Begin tracking a new AgentType stream immediately after it is created.
     * Idempotent — safe to call multiple times with the same ID.
     */
    fun trackAgentType(agentTypeId: AgentTypeId)
}

/**
 * Maintains the `agent_types` Postgres read model from KurrentDB AgentType events.
 *
 * Uses the same per-stream catch-up subscription strategy as [EffortSummaryProjection]:
 * - On coordinator startup, [start] reads all known AgentTypeIds from the DB and
 *   subscribes to each stream from position 0 (idempotent replay via upsert).
 * - When a new AgentType is created via the API, the route calls [trackAgentType]
 *   to start its subscription immediately after the write.
 *
 * [AgentTypeUpdated] carries only changed fields (null = unchanged), so
 * [patchUpdated] applies each field conditionally.
 *
 * See Architecture doc Section 8.2 and Event Schema doc Section 3.
 */
class AgentTypeSummaryProjectionImpl(
    private val eventStore: EventStore,
) : AgentTypeSummaryProjection {

    override val name = "agent-type-summary"
    override val streamPattern = "/agenttype:*"

    private val processedCount = AtomicLong(0)
    private val subscriptions = ConcurrentHashMap<String, EventSubscription>()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Start the projection by subscribing to all AgentType streams already known
     * in the read model. Called once by [ProjectionRunner] at coordinator startup.
     *
     * Always replays from position 0 (no durable checkpoint in Phase 6).
     * Upsert semantics guarantee idempotency on replay.
     */
    override suspend fun start() {
        val knownIds = dbQuery {
            AgentTypeTable
                .selectAll()
                .map { AgentTypeId(it[AgentTypeTable.agentTypeId]) }
        }
        logger.info("AgentTypeSummaryProjection starting: subscribing to {} known stream(s)", knownIds.size)
        knownIds.forEach { id -> subscribeToStream(id) }
    }

    override fun stop() {
        subscriptions.values.forEach { it.stop() }
        subscriptions.clear()
        logger.info("AgentTypeSummaryProjection stopped")
    }

    // ------------------------------------------------------------------ public API

    /**
     * Begin tracking a new AgentType stream via a catch-up subscription from position 0.
     * Idempotent: calling twice for the same ID has no effect.
     * Called by API routes immediately after a new AgentType is created.
     */
    override fun trackAgentType(agentTypeId: AgentTypeId) {
        val streamId = StreamNames.agentType(agentTypeId)
        if (!subscriptions.containsKey(streamId)) {
            subscribeToStream(agentTypeId)
        }
    }

    // ------------------------------------------------------------------ Projection interface

    override suspend fun handle(envelope: EventEnvelope) {
        val event = envelope.data as? AgentTypeEvent ?: return
        project(event)
    }

    override suspend fun checkpoint(): Long = processedCount.get()

    // ------------------------------------------------------------------ private helpers

    private fun subscribeToStream(agentTypeId: AgentTypeId) {
        val streamId = StreamNames.agentType(agentTypeId)
        subscriptions.computeIfAbsent(streamId) {
            eventStore.subscribeToStream(streamId, fromVersion = 0L) { envelope ->
                handle(envelope)
            }.also {
                logger.debug("AgentTypeSummaryProjection: started catch-up subscription on '{}'", streamId)
            }
        }
    }

    private suspend fun project(event: AgentTypeEvent) {
        when (event) {
            is AgentTypeCreated -> upsertCreated(event)
            is AgentTypeUpdated -> patchUpdated(event)
        }
        processedCount.incrementAndGet()
    }

    private suspend fun upsertCreated(event: AgentTypeCreated) {
        dbQuery {
            AgentTypeTable.upsert {
                it[agentTypeId]   = event.agentTypeId.value
                it[name]          = event.name
                it[model]         = event.model
                it[systemPrompt]  = event.systemPrompt
                it[maxTokens]     = event.maxTokens
                it[temperature]   = event.temperature ?: 0.0
                it[tools]         = json.encodeToString(event.tools ?: emptyList<String>())
                it[dockerImage]   = event.dockerImage
                it[spawnStrategy] = event.spawnStrategy
                it[createdAt]     = event.createdAt
                it[updatedAt]     = null
            }
        }
        logger.debug("AgentTypeSummaryProjection: upserted AgentTypeCreated for {}", event.agentTypeId)
    }

    /**
     * Apply a partial update — each nullable field in [AgentTypeUpdated] is only
     * written when non-null. This preserves values that were not changed.
     */
    private suspend fun patchUpdated(event: AgentTypeUpdated) {
        dbQuery {
            AgentTypeTable.update({ AgentTypeTable.agentTypeId eq event.agentTypeId.value }) { row ->
                event.name?.let         { row[name]          = it }
                event.model?.let        { row[model]         = it }
                event.systemPrompt?.let { row[systemPrompt]  = it }
                event.maxTokens?.let    { row[maxTokens]     = it }
                event.temperature?.let  { row[temperature]   = it }
                event.tools?.let        { row[tools]         = json.encodeToString(it) }
                event.dockerImage?.let  { row[dockerImage]   = it }
                event.spawnStrategy?.let { row[spawnStrategy] = it }
                row[updatedAt] = event.updatedAt
            }
        }
        logger.debug("AgentTypeSummaryProjection: patched AgentTypeUpdated for {}", event.agentTypeId)
    }
}
