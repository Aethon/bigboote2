package com.bigboote.events.eventstore

import com.bigboote.domain.aggregates.EventLogEntry

/**
 * Result of reading events from a stream.
 *
 * @param events The list of event envelopes read from the stream, in order.
 * @param lastStreamPosition The position of the last event read, or -1 if the stream is empty.
 */
data class ReadResult<E: Any>(
    val events: List<EventLogEntry<E>>,
    val lastStreamPosition: Long,
)
