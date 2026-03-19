//package com.bigboote.domain.aggregates
//
//import com.bigboote.domain.values.StreamName
//import kotlin.time.Instant
//
////abstract class EventBasedState<E : Any, S : EventBasedState<E, S>> {
////    abstract fun applyEvent(event: E): S
////}
//
//data class EventContext(
//    val streamName: StreamName<*>,
//    val streamPosition: Long,
//    val storePosition: Long
//)
//
//abstract class StreamBasedState<E : Any, S : StreamBasedState<E, S>> {
//    val streamPosition: Long
//    abstract val streamName: StreamName<E>
//
//    fun applyEvent(event: E, context: EventContext): S {
//
//    }
//
//    protected abstract fun applyEvent(event: E): S
//}
//
////abstract class StreamBasedState<E : Any, S : EventBasedState<E, S>>(
////    val streamPosition: Long,
////    val streamName: StreamName<E>,
////    val state: S
////) {
////    abstract fun applyEvent(event: E): S
////}
//
//abstract class StoreBasedState<E : Any, S>(val storePosition: Long, val state: S) {
//
//}