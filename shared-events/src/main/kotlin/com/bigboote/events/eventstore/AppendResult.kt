package com.bigboote.events.eventstore

/**
 * Result of appending events to a stream.
 *
 * @param nextExpectedVersion The version to use for the next append (optimistic concurrency).
 */
data class AppendResult(val nextExpectedVersion: Long)
