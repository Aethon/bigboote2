//package com.bigboote.domain.aggregates
//
//import com.bigboote.domain.values.StreamName
//
////
//////abstract class EventBasedState<E : Any, S : EventBasedState<E, S>> {
//////    abstract fun applyEvent(event: E): S
//////}
//
//data class EventContext(
//    val streamPosition: Long,
//    val storePosition: Long
//)
//
//interface RawEventLogEntry {
//    val streamName: StreamName<*>
//    val event: Any
//    val context: EventContext
//}
//
//interface EventLogEntry<out E : Any> :
//    RawEventLogEntry {
//    override val streamName: StreamName<E>
//    override val event: E
//}
//
//class EventLogEntryImpl<out E : Any>(
//    override val streamName: StreamName<E>,
//    override val event: E,
//    override val context: EventContext
//) : EventLogEntry<E> {
//}
//
////abstract class StreamState<E : Any, S : StreamState<E, S>> {
////
////    abstract val streamName: StreamName<E>
////    abstract val streamPosition: Long
////
////    fun apply(entry: EventLogEntry<E>): S = apply(entry.event, entry.context)
////
////    protected abstract fun apply(event: E, context: EventContext): S
////}
//
////abstract class StreamBasedState<E : Any, S : StreamBasedState<E, S>> {
////    val streamPosition: Long
////    abstract val streamName: StreamName<E>
////
////    fun applyEvent(event: E, context: EventContext): S {
////
////    }
////
////    protected abstract fun applyEvent(event: E): S
////}
////
//////abstract class StreamBasedState<E : Any, S : EventBasedState<E, S>>(
//////    val streamPosition: Long,
//////    val streamName: StreamName<E>,
//////    val state: S
//////) {
//////    abstract fun applyEvent(event: E): S
//////}
////
////abstract class StoreBasedState<E : Any, S>(val storePosition: Long, val state: S) {
////
////}
//
//abstract class StreamState<E : Any, S : StreamState<E, S>> {
//
//    fun apply(entry: EventLogEntry<E>): S = apply(entry.event, entry.context)
//
//    protected abstract fun apply(event: E, context: EventContext): S
//}
//
//abstract class NoContextStreamState<E : Any, S : NoContextStreamState<E, S>> :
//    StreamState<E, S>() {
//
//    final override fun apply(event: E, context: EventContext): S = apply(event)
//
//    protected abstract fun apply(event: E): S
//
//}
//
//data class StreamPositionState<E : Any, S : StreamState<E, S>>
//    (
//    val streamName: StreamName<E>,
//    val streamPosition: Long,
//    val state: S
//) {
//    fun apply(entry: EventLogEntry<E>): StreamPositionState<E, S> =
//        copy(state = state.apply(entry), streamPosition = entry.context.streamPosition)
//}
//
///**
// * Interface for starting a stream state from an event log entry.
// *
// * This interface is typically implemented by the companion object to the stream state class.
// */
//interface StreamStateStarter<E : Any, S : StreamState<E, S>> {
//    fun start(entry: EventLogEntry<E>): S
//}
