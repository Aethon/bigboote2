package com.bigboote.coordinator.aggregates

import com.bigboote.domain.aggregates.EventLogEntry
import com.bigboote.domain.values.StreamName
import com.bigboote.events.eventstore.AppendResult
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.eventstore.ExpectedVersion
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * Generic event-sourced aggregate repository.
 *
 * Loads aggregate state by replaying events from a KurrentDB stream, and
 * appends new events with optimistic concurrency control. Used by all
 * command handlers in the coordinator.
 *
 * See Architecture doc Section 6.1.
 */
class AggregateRepository(private val eventStore: EventStore) {

    /**
     * Load aggregate state by reading all events from [streamName] and folding
     * them through [apply]. Returns the reconstructed state and the last
     * stream version (-1 if the stream does not exist / is empty).
     */
    suspend fun <E : Any, S> load(
        eventKlass: KClass<E>,
        streamName: StreamName<E>,
        empty: S,
        apply: (S, E) -> S
    ): Pair<S, Long> {
        val result = eventStore.readStreamForward(eventKlass, streamName)
        val state = result.events.fold(empty) { s, env ->

            apply(s, eventKlass.cast(env))

        }
        return state to result.lastStreamPosition
    }

    suspend fun <E : Any, S> maybeLoad(
        eventKlass: KClass<E>,
        streamName: StreamName<E>,
        start: (EventLogEntry<E>) -> S,
        apply: (S, EventLogEntry<E>) -> S
    ): Pair<S, Long>? {
        val result = eventStore.readStreamForward(eventKlass, streamName)
        if (result.events.isEmpty()) return null

        val state = result.events.fold(null) { acc: S?, entry ->
            if (acc == null)
                start(entry)
            else
                apply(acc, entry)
        }

        return if (state == null) null else state to result.lastStreamPosition
    }

//    suspend fun <E : Any, S> load(
//        eventKlass: KClass<E>,
//        streamName: StreamName<E>,
//        streamPosition: Long? = null,
//        start: (E, EventContext) -> S,
//        apply: (S, E) -> S
//    ): Pair<S, Long> {
//        val result = readStreamRaw(streamName)
//        val state = result.events.fold(empty) { s, env -> apply(s, env.data) }
//        return state to result.lastStreamPosition
//    }

    /**
     * Append events to a stream with optimistic concurrency control.
     */
    suspend fun <E : Any> append(
        streamName: StreamName<E>,
        events: List<E>,
        expectedVersion: ExpectedVersion,
    ): AppendResult = eventStore.appendToStream(streamName, events, expectedVersion)
}

//class AggregateRepository2<E : Any>(private val klass: KClass<E>, private val eventStore: EventStore) {
//
//    /**
//     * Load aggregate state by reading all events from [streamName] and folding
//     * them through [apply]. Returns the reconstructed state and the last
//     * stream version (-1 if the stream does not exist / is empty).
//     */
//    suspend fun <S> load(
//        streamName: StreamName<E>,
//        empty: S,
//        apply: (S, E) -> S
//    ): Pair<S, Long> {
//        val result = eventStore.readStreamForward(streamName)
//        val state = result.events.fold(empty) { s, env ->
//
//            if (!klass.isInstance(env.data))
//                throw Exception("Expected event data to be of type ${klass.simpleName}, got ${env.data::class.simpleName}")
//
//            apply(s, klass.cast(env.data))
//
//        }
//        return state to result.lastStreamPosition
//    }
//
//
//    /**
//     * Append events to a stream with optimistic concurrency control.
//     */
//    suspend fun <E : Any> append(
//        streamName: StreamName<E>,
//        events: List<E>,
//        expectedVersion: ExpectedVersion,
//    ): AppendResult = eventStore.appendToStream(streamName, events, expectedVersion)
//
//    companion object {
//        fun make(): AggregateRepository2<ConversationState> = AggregateRepository2(ConversationState::class, EventStore())
//    }
//}