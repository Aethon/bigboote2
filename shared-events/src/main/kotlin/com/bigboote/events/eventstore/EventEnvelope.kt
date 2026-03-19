package com.bigboote.events.eventstore

import com.bigboote.domain.values.StreamName
import kotlinx.datetime.Instant

/**
 * Wrapper carrying a deserialized event along with its stream metadata.
 * Used by subscriptions and stream reads to provide context alongside the domain event.
 *
 * The type parameter [E] is the sealed event base for the stream (e.g. EffortEvent,
 * AgentEvent). Use [EventEnvelope<Any>] when the concrete type is unknown (e.g. in
 * [EventStore.subscribeToAll]).
 *
 * @param streamName The typed stream this event was read from — carries the context
 *                   (effortId, agentId, convId) that used to be duplicated in payloads.
 * @param eventType The event type name (e.g. "EffortCreated").
 * @param position The event's position within its stream.
 * @param data The deserialized domain event object.
 * @param timestamp The wall-clock time the event was written.
 */
data class EventEnvelope<out E : Any>(
    val streamName: StreamName<*>,
    val eventType: String,
    val position: Long,
    val data: E,
    val timestamp: Instant,
)
