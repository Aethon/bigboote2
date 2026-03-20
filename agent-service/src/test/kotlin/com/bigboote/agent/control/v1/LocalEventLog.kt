package com.bigboote.agent.control.v1

import com.bigboote.domain.events.Event
import com.bigboote.domain.events.EventContext
import com.bigboote.domain.events.EventLogEntry
import com.bigboote.domain.events.EventStream
import com.bigboote.domain.events.maybeCast
import com.bigboote.domain.values.StreamName
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass
import kotlin.time.Clock

class LocalEventLog(private val clock: Clock = Clock.System) :
    EventLog {

    private val mutex = Mutex()

    private val _events: MutableList<EventLogEntry<*>> = mutableListOf()

    private val _subscriptions: MutableList<(EventLogEntry<*>) -> Unit> = mutableListOf()

    override suspend fun <E : Event> emit(streamName: StreamName<E>, vararg events: E) {
        addEvents(streamName, *events).forEach { event -> _subscriptions.forEach { it(event) } }
    }

    private suspend fun <E : Event> addEvents(streamName: StreamName<E>, vararg events: E): List<EventLogEntry<*>> {
        return mutex.withLock {
            val now = clock.now()
            val next = _events.size.toLong()
            val newEvents =
                events.mapIndexed { index, event ->
                    EventLogEntry(
                        streamName,
                        event,
                        EventContext(next + index, next + index)
                    )
                }
            _events.addAll(newEvents)
            newEvents
        }
    }

    fun onNewEvent(block: (EventLogEntry<*>) -> Unit) {
        _subscriptions.add(block)
    }

    fun <E : Event> getStream(streamName: StreamName<E>, eventKlass: KClass<E>): EventStream<E> =
        StreamManager(streamName, eventKlass)

    override suspend fun readAfter(
        streamName: StreamName<*>,
        position: Long?,
        maxEntries: Int?
    ): List<EventLogEntry<*>> {
        return mutex.withLock {
            var filteredEvents = _events.toList()
            if (position != null)
                filteredEvents = filteredEvents.drop(position.toInt() + 1)
            filteredEvents = filteredEvents.filter { it.streamName == streamName }
            if (maxEntries != null)
                filteredEvents = filteredEvents.take(maxEntries)

            filteredEvents.toList()
        }
    }

    private inner class StreamManager<E : Event>(
        private val streamName: StreamName<E>,
        private val eventKlass: KClass<E>
    ) :
        EventStream<E> {
        override suspend fun readAfter(position: Long?, maxEntries: Int?): List<EventLogEntry<E>> =
            readAfter(streamName = streamName, position = position, maxEntries = maxEntries).map {
                it.maybeCast(eventKlass)
                    ?: throw IllegalArgumentException("Event ${eventKlass.simpleName} is not of type ${it.event::class.simpleName}")
            }

        override suspend fun emit(vararg events: E) {
            emit(streamName, *events)
        }
    }
}

