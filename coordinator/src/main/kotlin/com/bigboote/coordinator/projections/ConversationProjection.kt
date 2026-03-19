package com.bigboote.coordinator.projections

import com.bigboote.coordinator.projections.db.ConversationTable
import com.bigboote.coordinator.projections.db.MessageTable
import com.bigboote.domain.events.ConversationEvent
import com.bigboote.domain.events.ConversationEvent.*
import com.bigboote.domain.events.asConversationStream
import com.bigboote.domain.values.ConvId
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.StreamName
import com.bigboote.events.eventstore.EventEnvelope
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.eventstore.EventSubscription
import com.bigboote.infra.db.dbQuery
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val logger = LoggerFactory.getLogger(ConversationProjectionImpl::class.java)

/**
 * Public surface of the conversation read model projection consumed by route handlers.
 *
 * Extracted as an interface so that route-layer tests can mock it without
 * needing `open` or a real DB/EventStore. The sole production implementation
 * is [ConversationProjectionImpl].
 *
 * See Architecture doc Section 8.2.
 */
interface ConversationProjection : Projection {
    /** Begin tracking a newly created conversation stream (called from route handlers). */
    fun trackConversation(effortId: EffortId, convId: ConvId)
    /** Synchronously project one event for immediate read-your-writes consistency. */
    suspend fun project(event: ConversationEvent, stream: StreamName.Conversation)
}

/**
 * Production implementation of [ConversationProjection].
 *
 * Maintains the `conversations` and `messages` Postgres read models from KurrentDB
 * conversation events.
 *
 * Two delivery paths:
 * 1. **Direct** (`project(event, stream)`): called synchronously from API routes
 *    immediately after appending events to KurrentDB, ensuring immediate
 *    read-your-writes consistency.
 * 2. **Subscription** (`trackConversation(effortId, convId)` / `start()`): catch-up
 *    subscriptions from position 0 providing crash recovery and replay. All DB writes
 *    are idempotent upserts so double-delivery is harmless.
 *
 * [stream] provides [EffortId] and [ConvId] context no longer carried in event payloads.
 *
 * See Architecture doc Section 8.2.
 */
class ConversationProjectionImpl(
    private val eventStore: EventStore,
) : ConversationProjection {

    override val name = "conversation-summary"
    override val streamPattern = "/effort:*/conv:*"

    private val processedCount = AtomicLong(0)
    private val subscriptions  = ConcurrentHashMap<String, EventSubscription>()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Start the projection by subscribing to all conversation streams already known
     * in the read model. Called once by [ProjectionRunner] at coordinator startup.
     */
    override suspend fun start() {
        // Read all (effortId, convId) pairs from the conversations table
        val knownConversations: List<Pair<EffortId, ConvId>> = dbQuery {
            ConversationTable
                .selectAll()
                .map { row ->
                    EffortId(row[ConversationTable.effortId]) to
                        ConvId.parse(row[ConversationTable.convId])
                }
        }
        logger.info(
            "ConversationProjection starting: subscribing to {} known conversation stream(s)",
            knownConversations.size
        )
        knownConversations.forEach { (effortId, convId) ->
            subscribeToStream(effortId, convId)
        }
    }

    override fun stop() {
        subscriptions.values.forEach { it.stop() }
        subscriptions.clear()
        logger.info("ConversationProjection stopped")
    }

    // ------------------------------------------------------------------ public API

    /**
     * Begin tracking a new conversation stream via a catch-up subscription from position 0.
     * Idempotent: calling twice for the same stream has no effect.
     * Called by API routes immediately after a new conversation is created.
     */
    override fun trackConversation(effortId: EffortId, convId: ConvId) {
        val streamPath = StreamName.Conversation(effortId, convId).path
        if (!subscriptions.containsKey(streamPath)) {
            subscribeToStream(effortId, convId)
        }
    }

    /**
     * Synchronously project a single event into the read model.
     * Called from API routes for immediate read-your-writes consistency.
     * Also called internally by the subscription handler.
     *
     * [stream] provides [EffortId] and [ConvId] context no longer in event payloads.
     */
    override suspend fun project(event: ConversationEvent, stream: StreamName.Conversation) {
        when (event) {
            is ConversationCreated -> upsertConversation(event, stream)
            is MemberAdded        -> addMember(event, stream)
            is MessagePosted      -> upsertMessage(event, stream)
        }
        processedCount.incrementAndGet()
    }

    // ------------------------------------------------------------------ Projection interface

    override suspend fun handle(envelope: EventEnvelope<Any>) {
        val event = envelope.data as? ConversationEvent ?: return
        val stream = envelope.streamName.asConversationStream()
        project(event, stream)
    }

    override suspend fun checkpoint(): Long = processedCount.get()

    // ------------------------------------------------------------------ private helpers

    private fun subscribeToStream(effortId: EffortId, convId: ConvId) {
        val streamName = StreamName.Conversation(effortId, convId)
        subscriptions.computeIfAbsent(streamName.path) {
            eventStore.subscribeToStream(streamName, fromVersion = 0L) { envelope ->
                handle(envelope)
            }.also {
                logger.debug(
                    "ConversationProjection: started catch-up subscription on '{}'", streamName.path
                )
            }
        }
    }

    private suspend fun upsertConversation(event: ConversationCreated, stream: StreamName.Conversation) {
        val membersJson = json.encodeToString(event.members.map { it.toString() })
        dbQuery {
            ConversationTable.upsert {
                it[effortId]  = stream.effortId.value
                it[convId]    = stream.convId.value
                it[convName]  = event.convName.toString()
                it[members]   = membersJson
                it[createdAt] = event.createdAt
            }
        }
        logger.debug(
            "ConversationProjection: upserted ConversationCreated for {} in {}",
            stream.convId, stream.effortId
        )
    }

    private suspend fun addMember(event: MemberAdded, stream: StreamName.Conversation) {
        // Load current members, append the new one, write back.
        // Upsert semantics: if row doesn't exist yet (projection lag), we skip quietly.
        dbQuery {
            val row = ConversationTable
                .selectAll()
                .where {
                    (ConversationTable.effortId eq stream.effortId.value) and
                    (ConversationTable.convId eq stream.convId.value)
                }
                .singleOrNull() ?: return@dbQuery

            val currentMembers: List<String> =
                json.decodeFromString(row[ConversationTable.members])
            val memberStr = event.member.toString()
            if (memberStr in currentMembers) return@dbQuery   // already present (idempotent)

            val updatedJson = json.encodeToString(currentMembers + memberStr)
            ConversationTable.upsert {
                it[effortId]  = stream.effortId.value
                it[convId]    = stream.convId.value
                it[convName]  = row[ConversationTable.convName]
                it[members]   = updatedJson
                it[createdAt] = row[ConversationTable.createdAt]
            }
        }
        logger.debug(
            "ConversationProjection: member {} added to {} in {}",
            event.member, stream.convId, stream.effortId
        )
    }

    private suspend fun upsertMessage(event: MessagePosted, stream: StreamName.Conversation) {
        dbQuery {
            MessageTable.upsert {
                it[messageId] = event.messageId.value
                it[convId]    = stream.convId.value
                it[effortId]  = stream.effortId.value
                it[fromName]  = event.from.toString()
                it[body]      = event.body
                it[postedAt]  = event.postedAt
            }
        }
        logger.debug(
            "ConversationProjection: upserted MessagePosted {} in {} ({})",
            event.messageId, stream.convId, stream.effortId
        )
    }
}
