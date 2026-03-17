package com.bigboote.events.streams

/**
 * Type-safe wrapper for KurrentDB stream identifiers.
 * Provides a clear boundary between domain stream names (from StreamNames)
 * and raw string stream IDs used in the EventStore interface.
 *
 * @param value The full stream path, e.g. "/effort:V1StGXR8_Z"
 */
@JvmInline
value class StreamId(val value: String) {
    override fun toString(): String = value
}
