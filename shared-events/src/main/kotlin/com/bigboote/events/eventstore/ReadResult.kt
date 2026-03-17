package com.bigboote.events.eventstore

/**
 * Result of reading events from a stream.
 *
 * @param events The list of event envelopes read from the stream, in order.
 * @param lastStreamPosition The position of the last event read, or -1 if the stream is empty.
 */
data class ReadResult(
    val events: List<EventEnvelope>,
    val lastStreamPosition: Long,
)
