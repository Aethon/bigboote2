package com.bigboote.events.eventstore

/**
 * Optimistic concurrency control for event stream appends.
 */
sealed class ExpectedVersion {
    /** No concurrency check — any version is accepted. */
    data object Any : ExpectedVersion()

    /** Stream must not exist — this is the first write. */
    data object NoStream : ExpectedVersion()

    /** Stream must be at this exact version. */
    data class Exact(val version: Long) : ExpectedVersion()
}
