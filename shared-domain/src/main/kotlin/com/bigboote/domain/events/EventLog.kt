package com.bigboote.domain.events

import com.bigboote.domain.values.StreamName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * Data that describes an event in the event log.
 */
@Serializable
sealed interface Event

data class EventContext(
    val streamPosition: Long,
    val storePosition: Long
)

//interface RawEventLogEntry {
//    val streamName: StreamName<*>
//    val event: Event
//    val context: EventContext
//}
//
//interface EventLogEntry<out E : Event> :
//    RawEventLogEntry {
//    override val streamName: StreamName<E>
//    override val event: E
//}

class EventLogEntry<out E : Event>(
    val streamName: StreamName<E>,
    val event: E,
    val context: EventContext
) {
}

fun <E : Event> EventLogEntry<*>.maybeCast(eventKlass: KClass<E>): EventLogEntry<E>? {
    if (!eventKlass.isInstance(event))
        return null
    @Suppress("UNCHECKED_CAST") // just verified above
    return this as EventLogEntry<E>
}

abstract class StreamState<E : Event, S : StreamState<E, S>> {

    fun apply(entry: EventLogEntry<E>): S = apply(entry.event, entry.context)

    protected abstract fun apply(event: E, context: EventContext): S
}

abstract class NoContextStreamState<E : Event, S : NoContextStreamState<E, S>> :
    StreamState<E, S>() {

    final override fun apply(event: E, context: EventContext): S = apply(event)

    protected abstract fun apply(event: E): S

}

data class StreamPositionState<E : Event, S : StreamState<E, S>>
    (
    val streamName: StreamName<E>,
    val streamPosition: Long,
    val state: S
) {
    fun apply(entry: EventLogEntry<E>): StreamPositionState<E, S> =
        copy(state = state.apply(entry), streamPosition = entry.context.streamPosition)
}

/**
 * Interface for starting a stream state from an event log entry.
 *
 * This interface is typically implemented by the companion object to the stream state class.
 */
interface StreamStateStarter<E : Event, S : StreamState<E, S>> {
    fun start(entry: EventLogEntry<E>): S
}


//interface StreamBasedState<EVENT: Event> {
//    val position: Long?
//
//    fun applyEvent(event: EVENT, context: EventContext): StreamBasedState<EVENT>
//}
//
//data class EventContext(
//    val streamName: String,
//    val streamPosition: Long,
//    val timestamp: Instant
//)
//
//abstract class RawEventLogEntry {
//    abstract val context: EventContext
//    abstract val event: Event
//
//    inline fun <reified E : Event> cast(): EventLogEntry<E> {
//        if (event !is E)
//            throw IllegalArgumentException("Event ${event.javaClass.simpleName} is not of type ${E::class.simpleName}")
//        @Suppress("UNCHECKED_CAST")
//        return this as EventLogEntry<E>
//    }
//}
//
//data class EventLogEntry<E : Event>(
//    override val context: EventContext,
//    override val event: E
//) :
//    RawEventLogEntry()
//
//interface EventLogReader {
//    suspend fun readAfter(
//        streamName: String,
//        position: Long?,
//        maxEntries: Int?
//    ): List<RawEventLogEntry>
//}
//
//suspend inline fun <reified E : Event> EventLogReader.readAfter(
//    streamName: String,
//    position: Long?,
//    maxEntries: Int?
//): List<EventLogEntry<E>> {
//    return readAfter(streamName, position, maxEntries)
//        .map { it.cast<E>() }
//}
//
//interface EventLogWriter {
//    suspend fun <E : Event> emit(streamName: String, vararg events: E)
//}
//
//interface EventLog : EventLogReader, EventLogWriter

abstract class StoreState<S : StoreState<S>> {

    abstract fun apply(entry: EventLogEntry<*>): S
}

data class StorePositionState<S : StoreState<S>>(
    val storePosition: Long,
    val state: S
) {
    fun apply(entry: EventLogEntry<*>): StorePositionState<S> =
        copy(state = state.apply(entry), storePosition = entry.context.storePosition)
}

///**
// * Interface for starting a stream state from an event log entry.
// *
// * This interface is typically implemented by the companion object to the stream state class.
// */
//interface StoreStateStarter<S : StoreState<S>> {
//    fun start(entry: EventLogEntry<*>): S
//}
