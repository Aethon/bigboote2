package com.bigboote.events.eventstore

/**
 * Interface for event store operations against KurrentDB.
 *
 * All event-sourced aggregates read and write through this interface.
 * The implementation (KurrentEventStore in shared-infra) handles serialization
 * via EventSerializer/EventDeserializer.
 *
 * DECISION: Interface placed in shared-events (not shared-infra) per Implementation
 * Sequence Phase 2. shared-infra depends on shared-events, so the implementation
 * can reference this interface. This follows dependency inversion: abstractions live
 * closer to the domain, implementations in infrastructure.
 */
interface EventStore {

    /**
     * Append events to a stream with optimistic concurrency control.
     *
     * @param streamId The target stream (e.g. "/effort:V1StGXR8_Z")
     * @param events List of domain event instances (must be registered in EventRegistry)
     * @param expectedVersion Concurrency control: Any, NoStream, or Exact(version)
     * @return AppendResult containing the next expected version
     */
    suspend fun appendToStream(
        streamId: String,
        events: List<Any>,
        expectedVersion: ExpectedVersion = ExpectedVersion.Any,
    ): AppendResult

    /**
     * Read events from a stream in forward order.
     *
     * @param streamId The stream to read from
     * @param fromVersion Starting position (inclusive), default 0
     * @param maxCount Maximum number of events to read
     * @return ReadResult containing deserialized event envelopes
     */
    suspend fun readStreamForward(
        streamId: String,
        fromVersion: Long = 0L,
        maxCount: Int = Int.MAX_VALUE,
    ): ReadResult

    /**
     * Subscribe to a stream from a given position (catch-up subscription).
     * Used by Projections to populate read models.
     *
     * @param streamId The stream to subscribe to
     * @param fromVersion Starting position for the subscription
     * @param handler Callback invoked for each event envelope
     * @return EventSubscription handle to stop the subscription
     */
    fun subscribeToStream(
        streamId: String,
        fromVersion: Long,
        handler: suspend (EventEnvelope) -> Unit,
    ): EventSubscription

    /**
     * Create a persistent subscription (competing consumers).
     * Used by Reactors for at-least-once delivery with acknowledgement.
     *
     * @param streamId The stream to subscribe to (or "\$all" for all streams)
     * @param groupName The consumer group name
     * @param handler Callback invoked for each event envelope
     * @return EventSubscription handle to stop the subscription
     */
    fun subscribePersistent(
        streamId: String,
        groupName: String,
        handler: suspend (EventEnvelope) -> Unit,
    ): EventSubscription
}
