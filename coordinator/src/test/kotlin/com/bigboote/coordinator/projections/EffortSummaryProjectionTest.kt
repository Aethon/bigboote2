package com.bigboote.coordinator.projections

import com.bigboote.domain.values.EffortId
import com.bigboote.events.eventstore.EventEnvelope
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.eventstore.EventSubscription
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for [EffortSummaryProjection] that do not require a database.
 *
 * These tests cover the subscription management behaviours (trackEffort idempotency,
 * stop propagation) and the no-op fast-path for non-EffortEvent envelopes.
 * Database-write paths (upsertCreated, updateStatus) require a real Postgres
 * instance and are covered by integration tests in a later phase.
 */
class EffortSummaryProjectionTest : DescribeSpec({

    // ------------------------------------------------------------------ helpers

    fun makeProjection(): Pair<EffortSummaryProjection, EventStore> {
        val eventStore = mockk<EventStore>()
        val projection = EffortSummaryProjection(eventStore)
        return projection to eventStore
    }

    fun stubSubscription(eventStore: EventStore): EventSubscription {
        val sub = mockk<EventSubscription>(relaxed = true)
        every { eventStore.subscribeToStream(any(), any(), any()) } returns sub
        return sub
    }

    // ------------------------------------------------------------------ trackEffort

    describe("trackEffort") {
        it("starts a catch-up subscription on the effort stream") {
            val (projection, eventStore) = makeProjection()
            stubSubscription(eventStore)

            projection.trackEffort(EffortId("effort:abc"))

            // subscribeToStream called once with the correct stream id ("/effort:abc")
            verify(exactly = 1) { eventStore.subscribeToStream("/effort:abc", 0L, any()) }
        }

        it("does not create a duplicate subscription when called twice for the same effort") {
            val (projection, eventStore) = makeProjection()
            stubSubscription(eventStore)

            val effortId = EffortId("effort:dup")
            projection.trackEffort(effortId)
            projection.trackEffort(effortId)   // second call is a no-op

            verify(exactly = 1) { eventStore.subscribeToStream(any(), any(), any()) }
        }

        it("starts independent subscriptions for two different efforts") {
            val (projection, eventStore) = makeProjection()
            val sub1 = mockk<EventSubscription>(relaxed = true)
            val sub2 = mockk<EventSubscription>(relaxed = true)
            var call = 0
            every { eventStore.subscribeToStream(any(), any(), any()) } answers {
                if (call++ == 0) sub1 else sub2
            }

            projection.trackEffort(EffortId("effort:aaa"))
            projection.trackEffort(EffortId("effort:bbb"))

            verify(exactly = 1) { eventStore.subscribeToStream("/effort:aaa", 0L, any()) }
            verify(exactly = 1) { eventStore.subscribeToStream("/effort:bbb", 0L, any()) }
        }
    }

    // ------------------------------------------------------------------ stop

    describe("stop") {
        it("stops all active subscriptions") {
            val (projection, eventStore) = makeProjection()
            val sub1 = mockk<EventSubscription>(relaxed = true)
            val sub2 = mockk<EventSubscription>(relaxed = true)
            var call = 0
            every { eventStore.subscribeToStream(any(), any(), any()) } answers {
                if (call++ == 0) sub1 else sub2
            }

            projection.trackEffort(EffortId("effort:x1"))
            projection.trackEffort(EffortId("effort:x2"))

            projection.stop()

            verify(exactly = 1) { sub1.stop() }
            verify(exactly = 1) { sub2.stop() }
        }

        it("is safe to call stop when no subscriptions are active") {
            val (projection, _) = makeProjection()
            // Should not throw
            projection.stop()
        }
    }

    // ------------------------------------------------------------------ handle / non-effort event

    describe("handle with non-EffortEvent envelope") {
        it("returns without error when envelope data is not an EffortEvent") {
            val (projection, _) = makeProjection()
            val envelope = mockk<EventEnvelope>()
            // Return a plain String — definitely not an EffortEvent.
            // The cast `as? EffortEvent` returns null so the method exits early;
            // no dbQuery call is made, meaning no DB is needed for this assertion.
            every { envelope.data } returns "not-an-effort-event"

            runTest {
                projection.handle(envelope)
            }
        }
    }

    // ------------------------------------------------------------------ checkpoint

    describe("checkpoint") {
        it("returns zero before any events are processed") {
            val (projection, _) = makeProjection()
            runTest {
                projection.checkpoint() shouldBe 0L
            }
        }
    }
})
