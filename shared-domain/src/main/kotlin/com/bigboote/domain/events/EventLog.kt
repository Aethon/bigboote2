package com.bigboote.domain.events

import kotlin.time.Instant

interface Event

interface StreamBasedState<EVENT: Event> {
    val position: Long?

    fun applyEvent(event: EVENT, context: EventContext): StreamBasedState<EVENT>
}

data class EventContext(
    val streamName: String,
    val streamPosition: Long,
    val timestamp: Instant
)

abstract class RawEventLogEntry {
    abstract val context: EventContext
    abstract val event: Event

    inline fun <reified E : Event> cast(): EventLogEntry<E> {
        if (event !is E)
            throw IllegalArgumentException("Event ${event.javaClass.simpleName} is not of type ${E::class.simpleName}")
        @Suppress("UNCHECKED_CAST")
        return this as EventLogEntry<E>
    }
}

data class EventLogEntry<E : Event>(
    override val context: EventContext,
    override val event: E
) :
    RawEventLogEntry()

interface EventLogReader {
    suspend fun readAfter(
        streamName: String,
        position: Long?,
        maxEntries: Int?
    ): List<RawEventLogEntry>
}

suspend inline fun <reified E : Event> EventLogReader.readAfter(
    streamName: String,
    position: Long?,
    maxEntries: Int?
): List<EventLogEntry<E>> {
    return readAfter(streamName, position, maxEntries)
        .map { it.cast<E>() }
}

interface EventLogWriter {
    suspend fun <E : Event> emit(streamName: String, vararg events: E)
}

interface EventLog : EventLogReader, EventLogWriter