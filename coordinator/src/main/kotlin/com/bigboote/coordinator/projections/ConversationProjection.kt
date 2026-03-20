package com.bigboote.coordinator.projections

import com.bigboote.coordinator.projections.db.ConversationTable
import com.bigboote.coordinator.projections.db.MessageTable
import com.bigboote.domain.events.GroupChannelEvent
import com.bigboote.domain.events.GroupChannelEvent.*
import com.bigboote.domain.values.CollaboratorName
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
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val logger = LoggerFactory.getLogger(ConversationProjectionImpl::class.java)

/**
 * Public surface of the group-channel read model projection consumed by route handlers.
 *
 * Extracted as an interface so that route-layer tests can mock it without
 * needing `open` or a real DB/EventStore. The sole production implementation
 * is [ConversationProjectionImpl].
 *
 * See Architecture doc Section 8.2.
 */
interface ConversationProjection : Projection {
    /** Begin tracking a newly created channel stream (called from route handlers). */
    fun trackChannel(effortId: EffortId, channelName: CollaboratorName.Channel)
    /** Synchronously project one event for immediate read-your-writes consistency. */
    suspend fun project(event: GroupChannelEvent, stream: StreamName.GroupChannel, timestamp: Instant)
}

/**
 * Production implementation of [ConversationProjection].
 *
 * Maintains the `conversations` and `messages` Postgres read models from KurrentDB
 * group channel events.
 *
 * Two delivery paths:
 * 1. **Direct** (`project(event, stream, timestamp)`): called synchronously from API routes
 *    immediately after appending events to KurrentDB, ensuring immediate
 *    read-your-writes consistency.
 * 2. **Subscription** (`trackChannel(effortId, channelName)` / `start()`): per-stream
 *    catch-up subscriptions from position 0 providing crash recovery and replay.
 *    All DB writes are idempotent upserts so double-delivery is harmless.
 *
 * [stream] provides [EffortId] and channel-name context.
 *
 * See Architecture doc Section 8.2.
 */
class ConversationProjectionImpl(
    private val eventStore: EventStore,
) : ConversationProjection {

    override val name = "conversation-summary"
    override val streamPattern = "/eff:*/grp:*"

    private val processedCount = AtomicLong(0)
    private val subscriptions  = ConcurrentHashMap<String, EventSubscription>()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Start the projection by subscribing to all group channel streams already known
     * in the read model. Called once by [ProjectionRunner] at coordinator startup.
     */
    override suspend fun start() {
        val knownChannels: List<Pair<EffortId, CollaboratorName.Channel>> = dbQuery {
            ConversationTable
                .selectAll()
                .map { row ->
                    EffortId(row[ConversationTable.effortId]) to
                        CollaboratorName.Channel(row[ConversationTable.convId])
                }
        }
        logger.info(
            "ConversationProjection starting: subscribing to {} known channel stream(s)",
            knownChannels.size
        )
        knownChannels.forEach { (effortId, channelName) ->
            subscribeToStream(effortId, channelName)
        }
    }

    override fun stop() {
        subscriptions.values.forEach { it.stop() }
        subscriptions.clear()
        logger.info("ConversationProjection stopped")
    }

    // ------------------------------------------------------------------ public API

    /**
     * Begin tracking a new group channel stream via a catch-up subscription from position 0.
     * Idempotent: calling twice for the same stream has no effect.
     * Called by API routes immediately after a new channel is created.
     */
    override fun trackChannel(effortId: EffortId, channelName: CollaboratorName.Channel) {
        val streamPath = StreamName.GroupChannel(effortId, channelName).path
        if (!subscriptions.containsKey(streamPath)) {
            subscribeToStream(effortId, channelName)
        }
    }

    /**
     * Synchronously project a single event into the read model.
     * Called from API routes for immediate read-your-writes consistency.
     * Also called internally by the subscription handler.
     */
    override suspend fun project(
        event: GroupChannelEvent,
        stream: StreamName.GroupChannel,
        timestamp: Instant,
    ) {
        when (event) {
            is ChannelCreated    -> upsertChannel(event, stream, timestamp)
            is MembersAdded      -> addMembers(event, stream)
            is ChannelMessagePosted -> upsertMessage(event, stream, timestamp)
        }
        processedCount.incrementAndGet()
    }

    // ------------------------------------------------------------------ Projection interface

    override suspend fun handle(envelope: EventEnvelope<Any>) {
        val event = envelope.data as? GroupChannelEvent ?: return
        val stream = envelope.streamName as? StreamName.GroupChannel ?: run {
            logger.warn(
                "ConversationProjection: GroupChannelEvent on unexpected stream type: {}",
                envelope.streamName.path,
            )
            return
        }
        project(event, stream, envelope.timestamp)
    }

    override suspend fun checkpoint(): Long = processedCount.get()

    // ------------------------------------------------------------------ private helpers

    private fun subscribeToStream(effortId: EffortId, channelName: CollaboratorName.Channel) {
        val streamName = StreamName.GroupChannel(effortId, channelName)
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

    private suspend fun upsertChannel(
        event: ChannelCreated,
        stream: StreamName.GroupChannel,
        timestamp: Instant,
    ) {
        val membersJson = json.encodeToString(event.members.map { it.toString() })
        dbQuery {
            ConversationTable.upsert {
                it[effortId]  = stream.effortId.value
                it[convId]    = stream.channelName.simple
                it[convName]  = stream.channelName.toString()
                it[members]   = membersJson
                it[createdAt] = timestamp
            }
        }
        logger.debug(
            "ConversationProjection: upserted ChannelCreated for #{} in {}",
            stream.channelName.simple, stream.effortId
        )
    }

    private suspend fun addMembers(event: MembersAdded, stream: StreamName.GroupChannel) {
        dbQuery {
            val row = ConversationTable
                .selectAll()
                .where {
                    (ConversationTable.effortId eq stream.effortId.value) and
                    (ConversationTable.convId eq stream.channelName.simple)
                }
                .singleOrNull() ?: return@dbQuery

            val currentMembers: List<String> =
                json.decodeFromString(row[ConversationTable.members])
            val newMemberStrs = event.members
                .map { it.toString() }
                .filter { it !in currentMembers }
            if (newMemberStrs.isEmpty()) return@dbQuery   // idempotent

            val updatedJson = json.encodeToString(currentMembers + newMemberStrs)
            ConversationTable.upsert {
                it[effortId]  = stream.effortId.value
                it[convId]    = stream.channelName.simple
                it[convName]  = row[ConversationTable.convName]
                it[members]   = updatedJson
                it[createdAt] = row[ConversationTable.createdAt]
            }
        }
        logger.debug(
            "ConversationProjection: {} member(s) added to #{} in {}",
            event.members.size, stream.channelName.simple, stream.effortId
        )
    }

    private suspend fun upsertMessage(
        event: ChannelMessagePosted,
        stream: StreamName.GroupChannel,
        timestamp: Instant,
    ) {
        dbQuery {
            MessageTable.upsert {
                it[messageId] = event.messageId.value
                it[convId]    = stream.channelName.simple
                it[effortId]  = stream.effortId.value
                it[fromName]  = event.from.toString()
                it[body]      = event.body
                it[postedAt]  = timestamp
            }
        }
        logger.debug(
            "ConversationProjection: upserted ChannelMessagePosted {} in #{} ({})",
            event.messageId, stream.channelName.simple, stream.effortId
        )
    }
}
