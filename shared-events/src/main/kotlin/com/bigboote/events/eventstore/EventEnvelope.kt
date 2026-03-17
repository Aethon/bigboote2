package com.bigboote.events.eventstore

import kotlinx.datetime.Instant

/**
 * Wrapper carrying a deserialized event along with its stream metadata.
 * Used by subscriptions and stream reads to provide context alongside the domain event.
 *
 * @param streamId The KurrentDB stream this event was read from.
 * @param eventType The event type name (e.g. "EffortCreated").
 * @param position The event's position within its stream.
 * @param data The deserialized domain event object.
 * @param timestamp The wall-clock time the event was written.
 */
data class EventEnvelope(
    val streamId: String,
    val eventType: String,
    val position: Long,
    val data: Any,
    val timestamp: Instant,
)
