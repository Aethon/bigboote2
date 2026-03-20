package com.bigboote.agent.control.v1

import com.bigboote.domain.events.Event
import com.bigboote.domain.events.EventContext
import com.bigboote.domain.events.EventLogEntry
import com.bigboote.domain.values.StreamName
import kotlin.collections.map

//data class EventLogEntry<E : Event>(
//    override val context: EventContext,
//    override val event: E
//) :
//    RawEventLogEntry()

interface EventLogReader {
    suspend fun readAfter(
        streamName: StreamName<*>,
        position: Long?,
        maxEntries: Int?
    ): List<EventLogEntry<*>>
}

//suspend inline fun <reified E : Event> EventLogReader.readAfter(
//    streamName: StreamName<E>,
//    position: Long?,
//    maxEntries: Int?
//): List<EventLogEntry<E>> {
//    return readAfter(streamName, position, maxEntries)
//        .map { it.cast<E>() }
//}

interface EventLogWriter {
    suspend fun <E : Event> emit(streamName: StreamName<E>, vararg events: E)
}

interface EventLog : EventLogReader, EventLogWriter
