package com.bigboote.domain.events

interface EventStreamWriter<E: Event> {
    suspend fun emit(vararg events: E)
}

interface EventStreamReader<E:Event> {
    suspend fun readAfter(position: Long?, maxEntries: Int?): List<EventLogEntry<E>>
}

//suspend inline fun <reified E : Event> EventStreamReader.readAfter(
//    position: Long?,
//    maxEntries: Int?
//): List<EventLogEntry<E>> {
//    return readAfter(position, maxEntries)
//        .map {
//            it.maybeCast(E::class)
//                ?: throw IllegalArgumentException("Event ${E::class.simpleName} is not of type ${it.event::class.simpleName}")
//        }
//}

interface EventStream<E: Event> : EventStreamReader<E>, EventStreamWriter<E>