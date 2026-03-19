package com.bigboote.infra.eventstore

import com.bigboote.domain.aggregates.EventContext
import com.bigboote.domain.aggregates.EventLogEntry
import com.bigboote.domain.aggregates.EventLogEntryImpl
import com.bigboote.domain.values.StreamName
import com.bigboote.events.eventstore.AppendResult
import com.bigboote.events.eventstore.EventEnvelope
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.eventstore.EventSubscription
import com.bigboote.events.eventstore.ExpectedVersion
import com.bigboote.events.eventstore.ReadResult
import com.bigboote.events.serialization.EventDeserializer
import com.bigboote.events.serialization.EventSerializer
import com.eventstore.dbclient.AppendToStreamOptions
import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBPersistentSubscriptionsClient
import com.eventstore.dbclient.ExpectedRevision
import com.eventstore.dbclient.NackAction
import com.eventstore.dbclient.PersistentSubscription
import com.eventstore.dbclient.PersistentSubscriptionListener
import com.eventstore.dbclient.ReadStreamOptions
import com.eventstore.dbclient.ResolvedEvent
import com.eventstore.dbclient.Subscription
import com.eventstore.dbclient.SubscribeToAllOptions
import com.eventstore.dbclient.SubscribeToStreamOptions
import com.eventstore.dbclient.SubscriptionListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.full.cast

/**
 * KurrentDB implementation of the [EventStore] interface.
 *
 * Delegates to the com.eventstore:db-client-java client (v5.x).
 * Serialization/deserialization is handled by EventSerializer and EventDeserializer
 * from shared-events.
 *
 * Stream names are typed [StreamName] instances; the [StreamName.path] property is
 * used as the raw KurrentDB stream ID on the wire.
 *
 * See Architecture doc Change Document v1.0 Section 6.
 */
class KurrentEventStore(
    private val client: EventStoreDBClient,
    private val persistentSubscriptionsClient: EventStoreDBPersistentSubscriptionsClient,
) : EventStore {

    private val logger = LoggerFactory.getLogger(KurrentEventStore::class.java)
    private val subscriptionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("UNCHECKED_CAST")
    override suspend fun <E : Any> appendToStream(
        streamName: StreamName<E>,
        events: List<E>,
        expectedVersion: ExpectedVersion,
    ): AppendResult {
        val streamId = streamName.path
        val eventDataList = events.map { EventSerializer.serialize(it) }.toTypedArray()

        val options = AppendToStreamOptions.get()
            .expectedRevision(expectedVersion.toExpectedRevision())

        val writeResult = client.appendToStream(streamId, options, *eventDataList).await()
        val nextExpected = writeResult.nextExpectedRevision.toRawLong()

        logger.debug(
            "Appended {} event(s) to stream '{}', nextExpectedRevision={}",
            events.size, streamId, nextExpected
        )

        return AppendResult(nextExpectedVersion = nextExpected)
    }

    override suspend fun <E: Any> readStreamForward(
        eventKlass: KClass<E>,
        streamName: StreamName<E>,
        fromVersion: Long,
        maxCount: Int,
    ): ReadResult<E> {
        val streamId = streamName.path
        val options = ReadStreamOptions.get()
            .forwards()
            .fromRevision(fromVersion)
            .maxCount(maxCount.toLong())

        val readResult = try {
            client.readStream(streamId, options).await()
        } catch (e: java.util.concurrent.ExecutionException) {
            // Stream not found returns an empty result
            if (e.cause is com.eventstore.dbclient.StreamNotFoundException) {
                return ReadResult(events = emptyList(), lastStreamPosition = -1L)
            }
            throw e
        }

        val events = readResult.events
        val envelopes = events.map { it.toStreamEntry(streamName, eventKlass) }
        val lastPosition = if (events.isEmpty()) -1L
            else events.last().originalEvent.revision

        return ReadResult(events = envelopes, lastStreamPosition = lastPosition)
    }

    override fun <E : Any> subscribeToStream(
        streamName: StreamName<E>,
        fromVersion: Long,
        handler: suspend (EventEnvelope<E>) -> Unit,
    ): EventSubscription {
        val streamId = streamName.path
        val options = SubscribeToStreamOptions.get().fromRevision(fromVersion)

        val listener = object : SubscriptionListener() {
            override fun onEvent(subscription: Subscription, event: ResolvedEvent) {
                subscriptionScope.launch {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        handler(event.toEventEnvelope(streamName) as EventEnvelope<E>)
                    } catch (e: Exception) {
                        logger.error(
                            "Error processing event in catch-up subscription on '{}'", streamId, e
                        )
                    }
                }
            }

            override fun onCancelled(subscription: Subscription?, throwable: Throwable?) {
                if (throwable != null) {
                    logger.error("Catch-up subscription error on stream '{}'", streamId, throwable)
                } else {
                    logger.info("Catch-up subscription on stream '{}' cancelled", streamId)
                }
            }
        }

        val subscription = client.subscribeToStream(streamId, listener, options).get()

        logger.info("Started catch-up subscription on stream '{}' from version {}", streamId, fromVersion)
        return KurrentEventSubscription(subscription)
    }

    override fun <E : Any> subscribePersistent(
        streamName: StreamName<E>,
        groupName: String,
        handler: suspend (EventEnvelope<E>) -> Unit,
    ): EventSubscription {
        val streamId = streamName.path
        val listener = object : PersistentSubscriptionListener() {
            override fun onEvent(
                subscription: PersistentSubscription,
                retryCount: Int,
                event: ResolvedEvent,
            ) {
                subscriptionScope.launch {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        handler(event.toEventEnvelope(streamName) as EventEnvelope<E>)
                        subscription.ack(event)
                    } catch (e: Exception) {
                        logger.error(
                            "Error processing event in persistent subscription '{}' on '{}' (retry {})",
                            groupName, streamId, retryCount, e
                        )
                        subscription.nack(NackAction.Retry, e.message ?: "Processing error", event)
                    }
                }
            }

            override fun onCancelled(subscription: PersistentSubscription?, throwable: Throwable?) {
                if (throwable != null) {
                    logger.error(
                        "Persistent subscription '{}' error on stream '{}'", groupName, streamId, throwable
                    )
                } else {
                    logger.info("Persistent subscription '{}' on stream '{}' cancelled", groupName, streamId)
                }
            }
        }

        val subscription = persistentSubscriptionsClient.subscribeToStream(streamId, groupName, listener).get()

        logger.info("Started persistent subscription '{}' on stream '{}'", groupName, streamId)
        return PersistentEventSubscription(subscription)
    }

    override fun subscribeToAll(
        handler: suspend (EventEnvelope<Any>) -> Unit,
    ): EventSubscription {
        val options = SubscribeToAllOptions.get().fromStart()

        val listener = object : SubscriptionListener() {
            override fun onEvent(subscription: Subscription, event: ResolvedEvent) {
                // Skip KurrentDB system events (prefixed with $)
                val streamId = event.originalEvent.streamId
                if (streamId.startsWith("\$")) return

                subscriptionScope.launch {
                    try {
                        handler(event.toEventEnvelopeFromAll())
                    } catch (e: Exception) {
                        logger.error(
                            "Error processing event in \$all subscription (stream='{}')", streamId, e
                        )
                    }
                }
            }

            override fun onCancelled(subscription: Subscription?, throwable: Throwable?) {
                if (throwable != null) {
                    logger.error("Catch-up \$all subscription error", throwable)
                } else {
                    logger.info("Catch-up \$all subscription cancelled")
                }
            }
        }

        val subscription = client.subscribeToAll(listener, options).get()

        logger.info("Started catch-up \$all subscription")
        return KurrentEventSubscription(subscription)
    }

    // ---- Private helpers ----------------------------------------------------------------

    /**
     * Convert a [ResolvedEvent] to an [EventEnvelope] for a known typed stream.
     * The [streamName] carries the contextual identity (effortId, agentId, etc.).
     */
    private fun ResolvedEvent.toEventEnvelope(streamName: StreamName<*>): EventEnvelope<Any> {
        val recorded = this.originalEvent
        val data = EventDeserializer.deserialize(recorded)
        return EventEnvelope(
            streamName = streamName,
            eventType = recorded.eventType,
            position = recorded.revision,
            data = data,
            timestamp = Instant.fromEpochMilliseconds(recorded.created.toEpochMilli()),
        )
    }

    /**
     * Convert a [ResolvedEvent] to an [EventEnvelope] for a known typed stream.
     * The [streamName] carries the contextual identity (effortId, agentId, etc.).
     */
    private fun <E: Any> ResolvedEvent.toStreamEntry(streamName: StreamName<E>, eventKlass: KClass<E>): EventLogEntry<E> {
        val recorded = this.originalEvent
        val data = EventDeserializer.deserialize(recorded)
        return EventLogEntryImpl(
            streamName = streamName,
            event = eventKlass.cast(data),
            context = EventContext(
                streamPosition = recorded.revision,
                storePosition = recorded.position.commitUnsigned
            )
        )
    }

    /**
     * Convert a [ResolvedEvent] from a `\$all` subscription to an [EventEnvelope].
     * Parses the stream name from [ResolvedEvent.originalEvent.streamId] via [StreamName.parse].
     * Unknown/system stream paths are silently skipped at the [subscribeToAll] call site.
     */
    private fun ResolvedEvent.toEventEnvelopeFromAll(): EventEnvelope<Any> {
        val recorded = this.originalEvent
        val streamName = StreamName.parse(recorded.streamId)
        val data = EventDeserializer.deserialize(recorded)
        return EventEnvelope(
            streamName = streamName,
            eventType = recorded.eventType,
            position = recorded.revision,
            data = data,
            timestamp = Instant.fromEpochMilliseconds(recorded.created.toEpochMilli()),
        )
    }

    private fun ExpectedVersion.toExpectedRevision(): ExpectedRevision = when (this) {
        is ExpectedVersion.Any -> ExpectedRevision.any()
        is ExpectedVersion.NoStream -> ExpectedRevision.noStream()
        is ExpectedVersion.Exact -> ExpectedRevision.expectedRevision(version)
    }
}

/**
 * Wraps a KurrentDB catch-up [Subscription] as an [EventSubscription].
 */
private class KurrentEventSubscription(
    private val subscription: Subscription,
) : EventSubscription {
    override fun stop() {
        subscription.stop()
    }
}

/**
 * Wraps a KurrentDB [PersistentSubscription] as an [EventSubscription].
 */
private class PersistentEventSubscription(
    private val subscription: PersistentSubscription,
) : EventSubscription {
    override fun stop() {
        subscription.stop()
    }
}
