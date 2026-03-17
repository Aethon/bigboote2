package com.bigboote.infra.eventstore

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
import com.eventstore.dbclient.ReadStreamOptions
import com.eventstore.dbclient.ResolvedEvent
import com.eventstore.dbclient.SubscribeToStreamOptions
import com.eventstore.dbclient.NackAction
import com.eventstore.dbclient.PersistentSubscription
import com.eventstore.dbclient.PersistentSubscriptionListener
import com.eventstore.dbclient.Subscription
import com.eventstore.dbclient.SubscriptionListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory

/**
 * KurrentDB implementation of the EventStore interface.
 *
 * Delegates to the com.eventstore:db-client-java client (v5.x).
 * Serialization/deserialization is handled by EventSerializer and EventDeserializer
 * from shared-events.
 */
class KurrentEventStore(
    private val client: EventStoreDBClient,
    private val persistentSubscriptionsClient: EventStoreDBPersistentSubscriptionsClient,
) : EventStore {

    private val logger = LoggerFactory.getLogger(KurrentEventStore::class.java)
    private val subscriptionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun appendToStream(
        streamId: String,
        events: List<Any>,
        expectedVersion: ExpectedVersion,
    ): AppendResult {
        val eventDataList = events.map { EventSerializer.serialize(it) }
            .toTypedArray()

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

    override suspend fun readStreamForward(
        streamId: String,
        fromVersion: Long,
        maxCount: Int,
    ): ReadResult {
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
        val envelopes = events.map { it.toEventEnvelope(streamId) }
        val lastPosition = if (events.isEmpty()) -1L
            else events.last().originalEvent.revision

        return ReadResult(events = envelopes, lastStreamPosition = lastPosition)
    }

    override fun subscribeToStream(
        streamId: String,
        fromVersion: Long,
        handler: suspend (EventEnvelope) -> Unit,
    ): EventSubscription {
        val options = SubscribeToStreamOptions.get()
            .fromRevision(fromVersion)

        val listener = object : SubscriptionListener() {
            override fun onEvent(subscription: Subscription, event: ResolvedEvent) {
                subscriptionScope.launch {
                    try {
                        handler(event.toEventEnvelope(streamId))
                    } catch (e: Exception) {
                        logger.error("Error processing event in catch-up subscription on '{}'", streamId, e)
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

    override fun subscribePersistent(
        streamId: String,
        groupName: String,
        handler: suspend (EventEnvelope) -> Unit,
    ): EventSubscription {
        val listener = object : PersistentSubscriptionListener() {
            override fun onEvent(subscription: PersistentSubscription, retryCount: Int, event: ResolvedEvent) {
                subscriptionScope.launch {
                    try {
                        handler(event.toEventEnvelope(streamId))
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
                        "Persistent subscription '{}' error on stream '{}'",
                        groupName, streamId, throwable
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

    private fun ResolvedEvent.toEventEnvelope(streamId: String): EventEnvelope {
        val recorded = this.originalEvent
        val data = EventDeserializer.deserialize(recorded)
        return EventEnvelope(
            streamId = streamId,
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
 * Wraps a KurrentDB catch-up Subscription as an EventSubscription.
 */
private class KurrentEventSubscription(
    private val subscription: Subscription,
) : EventSubscription {
    override fun stop() {
        subscription.stop()
    }
}

/**
 * Wraps a KurrentDB PersistentSubscription as an EventSubscription.
 */
private class PersistentEventSubscription(
    private val subscription: PersistentSubscription,
) : EventSubscription {
    override fun stop() {
        subscription.stop()
    }
}
