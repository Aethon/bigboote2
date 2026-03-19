package com.bigboote.domain.events

interface EventStreamWriter {
    suspend fun <E: Event>emit(vararg events: E)
}

interface EventStreamReader {
    suspend fun readAfter(position: Long?, maxEntries: Int?): List<RawEventLogEntry>
}

suspend inline fun <reified E : Event> EventStreamReader.readAfter(
    position: Long?,
    maxEntries: Int?
): List<EventLogEntry<E>> {
    return readAfter(position, maxEntries)
        .map { it.cast<E>() }
}

interface EventStream: EventStreamReader, EventStreamWriter