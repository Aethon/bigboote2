package com.bigboote.coordinator.aggregates

import com.bigboote.domain.values.StreamName
import com.bigboote.events.eventstore.AppendResult
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.eventstore.ExpectedVersion

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
    suspend fun <S> load(
        streamName: StreamName<*>,
        empty: S,
        apply: (S, Any) -> S,
    ): Pair<S, Long> {
        val result = eventStore.readStreamForward(streamName)
        val state = result.events.fold(empty) { s, env -> apply(s, env.data) }
        return state to result.lastStreamPosition
    }

    /**
     * Append events to a stream with optimistic concurrency control.
     */
    suspend fun <E : Any> append(
        streamName: StreamName<E>,
        events: List<E>,
        expectedVersion: ExpectedVersion,
    ): AppendResult = eventStore.appendToStream(streamName, events, expectedVersion)
}
