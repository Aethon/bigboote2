package com.bigboote.events.eventstore

import com.bigboote.domain.values.StreamName

/**
 * Interface for event store operations against KurrentDB.
 *
 * All event-sourced aggregates read and write through this interface.
 * The implementation (KurrentEventStore in shared-infra) handles serialization
 * via EventSerializer/EventDeserializer.
 *
 * The type parameter on [StreamName] encodes which event type is valid for each stream,
 * giving compile-time alignment between stream selection and event lists at the call site.
 *
 * DECISION: Interface placed in shared-events (not shared-infra) per Implementation
 * Sequence Phase 2. shared-infra depends on shared-events, so the implementation
 * can reference this interface. This follows dependency inversion: abstractions live
 * closer to the domain, implementations in infrastructure.
 *
 * See Architecture doc Change Document v1.0 Section 3.
 */
interface EventStore {

    /**
     * Append events to a stream with optimistic concurrency control.
     *
     * @param streamName The typed target stream (e.g. StreamName.Effort(effortId))
     * @param events List of domain event instances (must be registered in EventRegistry)
     * @param expectedVersion Concurrency control: Any, NoStream, or Exact(version)
     * @return AppendResult containing the next expected version
     */
    suspend fun <E : Any> appendToStream(
        streamName: StreamName<E>,
        events: List<E>,
        expectedVersion: ExpectedVersion = ExpectedVersion.Any,
    ): AppendResult

    /**
     * Read events from a stream in forward order.
     *
     * @param streamName The typed stream to read from
     * @param fromVersion Starting position (inclusive), default 0
     * @param maxCount Maximum number of events to read
     * @return ReadResult containing deserialized event envelopes
     */
    suspend fun readStreamForward(
        streamName: StreamName<*>,
        fromVersion: Long = 0L,
        maxCount: Int = Int.MAX_VALUE,
    ): ReadResult

    /**
     * Subscribe to a stream from a given position (catch-up subscription).
     * Used by Projections to populate read models.
     *
     * @param streamName The typed stream to subscribe to
     * @param fromVersion Starting position for the subscription
     * @param handler Callback invoked for each typed event envelope
     * @return EventSubscription handle to stop the subscription
     */
    fun <E : Any> subscribeToStream(
        streamName: StreamName<E>,
        fromVersion: Long,
        handler: suspend (EventEnvelope<E>) -> Unit,
    ): EventSubscription

    /**
     * Create a persistent subscription (competing consumers) on a single typed stream.
     * Used by Reactors for at-least-once delivery with acknowledgement.
     *
     * @param streamName The typed stream to subscribe to
     * @param groupName The consumer group name
     * @param handler Callback invoked for each typed event envelope
     * @return EventSubscription handle to stop the subscription
     */
    fun <E : Any> subscribePersistent(
        streamName: StreamName<E>,
        groupName: String,
        handler: suspend (EventEnvelope<E>) -> Unit,
    ): EventSubscription

    /**
     * Subscribe to the KurrentDB \$all stream (catch-up).
     * Used by Reactors that need to react across multiple stream types (cross-cutting concerns).
     *
     * Envelopes arrive with [EventEnvelope.data] typed as [Any]; use the [streamAs] helpers
     * on each event sealed base to safely cast [EventEnvelope.streamName] and dispatch.
     *
     * @param handler Callback invoked for each event envelope from any stream
     * @return EventSubscription handle to stop the subscription
     */
    fun subscribeToAll(
        handler: suspend (EventEnvelope<Any>) -> Unit,
    ): EventSubscription
}
