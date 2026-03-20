package com.bigboote.coordinator.reactors

import com.bigboote.coordinator.proxy.CollaboratorProxy
import com.bigboote.coordinator.proxy.ProxyRegistry
import com.bigboote.domain.events.DirectMessageEvent
import com.bigboote.domain.events.GroupChannelEvent
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.MessageId
import com.bigboote.domain.values.StreamName
import com.bigboote.events.eventstore.EventEnvelope
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.eventstore.EventSubscription
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.*
import kotlinx.datetime.Instant

/**
 * Unit tests for [MessageDeliveryReactor].
 *
 * Verifies that the reactor correctly:
 *  - Subscribes to `$all` on start via [EventStore.subscribeToAll].
 *  - Ignores non-message events silently.
 *  - Calls [CollaboratorProxy.deliverChannelMessage] for each recipient in
 *    [GroupChannelEvent.ChannelMessagePosted.to] that has a registered proxy.
 *  - Silently skips recipients whose proxy is not registered (offline/unspawned).
 *  - Handles delivery failures gracefully (logs, does not crash the reactor).
 *  - Calls [CollaboratorProxy.deliverDirectMessage] for the DM recipient in
 *    [StreamName.DirectMessage.collaboratorName].
 *  - Stops the subscription on [MessageDeliveryReactor.stop].
 *
 * Recipients are extracted from [GroupChannelEvent.ChannelMessagePosted.to] (already
 * excludes the sender) or from [StreamName.DirectMessage.collaboratorName] — no
 * read-model query is needed.
 *
 * No real KurrentDB, Postgres, or WebSocket connections are required.
 */
class MessageDeliveryReactorTest : DescribeSpec({

    // ---- fixtures ----

    val effortId    = EffortId("test-effort-001")
    val channelName = CollaboratorName.Channel("general")
    val senderName  = CollaboratorName.Individual("alice")
    val member1Name = CollaboratorName.Individual("bob")
    val member2Name = CollaboratorName.Individual("charlie")
    val timestamp   = Instant.fromEpochMilliseconds(1000)

    val channelStream = StreamName.GroupChannel(effortId, channelName)

    val channelEvent = GroupChannelEvent.ChannelMessagePosted(
        messageId = MessageId("test-msg-001"),
        from      = senderName,
        to        = setOf(member1Name, member2Name),  // sender already excluded
        body      = "Hello channel!",
    )

    // ---- helpers ----

    /** Create a mock EventStore that captures the subscription handler. */
    fun mockEventStore(captureSlot: CapturingSlot<suspend (EventEnvelope<Any>) -> Unit>): EventStore {
        val mockSubscription = mockk<EventSubscription>(relaxed = true)
        val eventStore = mockk<EventStore>()
        every { eventStore.subscribeToAll(capture(captureSlot)) } returns mockSubscription
        return eventStore
    }

    /** Build a channel message envelope. */
    fun makeChannelEnvelope(data: Any): EventEnvelope<Any> = EventEnvelope(
        streamName = channelStream,
        eventType  = data::class.simpleName ?: "Unknown",
        position   = 0L,
        data       = data,
        timestamp  = timestamp,
    )

    /** Build a DM envelope. */
    fun makeDmEnvelope(data: Any, recipient: CollaboratorName.Individual): EventEnvelope<Any> = EventEnvelope(
        streamName = StreamName.DirectMessage(effortId, recipient),
        eventType  = data::class.simpleName ?: "Unknown",
        position   = 0L,
        data       = data,
        timestamp  = timestamp,
    )

    /** Build a reactor with mocked dependencies. */
    fun makeReactor(
        eventStore: EventStore,
        registry: ProxyRegistry = ProxyRegistry(),
    ) = MessageDeliveryReactor(
        eventStore    = eventStore,
        proxyRegistry = registry,
    )

    // ---- tests ----

    describe("MessageDeliveryReactor.start") {

        it("subscribes to \$all on start") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore  = mockEventStore(handlerSlot)
            val reactor     = makeReactor(eventStore)

            reactor.start()

            verify { eventStore.subscribeToAll(any()) }
        }
    }

    describe("MessageDeliveryReactor channel message handling") {

        it("ignores events that are not ChannelMessagePosted or DirectMessagePosted") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore  = mockEventStore(handlerSlot)
            val registry    = mockk<ProxyRegistry>(relaxed = true)
            val reactor     = makeReactor(eventStore, registry)
            reactor.start()

            handlerSlot.captured(makeChannelEnvelope("some-other-event"))

            verify(exactly = 0) { registry.get(any(), any()) }
        }

        it("delivers channel message to all recipients in event.to") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore  = mockEventStore(handlerSlot)

            val proxy1 = mockk<CollaboratorProxy>(relaxed = true)
            val proxy2 = mockk<CollaboratorProxy>(relaxed = true)

            val registry = ProxyRegistry()
            registry.register(effortId, member1Name, proxy1)
            registry.register(effortId, member2Name, proxy2)

            val reactor = makeReactor(eventStore, registry)
            reactor.start()

            handlerSlot.captured(makeChannelEnvelope(channelEvent))

            coVerify(exactly = 1) { proxy1.deliverChannelMessage(channelStream, channelEvent, timestamp) }
            coVerify(exactly = 1) { proxy2.deliverChannelMessage(channelStream, channelEvent, timestamp) }
        }

        it("silently skips recipients with no registered proxy") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore  = mockEventStore(handlerSlot)

            // Only member1 has a proxy; member2 is offline / not yet spawned
            val proxy1   = mockk<CollaboratorProxy>(relaxed = true)
            val registry = ProxyRegistry()
            registry.register(effortId, member1Name, proxy1)

            val reactor = makeReactor(eventStore, registry)
            reactor.start()

            // Should not throw despite member2 having no proxy
            handlerSlot.captured(makeChannelEnvelope(channelEvent))

            coVerify(exactly = 1) { proxy1.deliverChannelMessage(channelStream, channelEvent, timestamp) }
        }

        it("handles deliverChannelMessage failures without crashing the reactor") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore  = mockEventStore(handlerSlot)

            val failingProxy = mockk<CollaboratorProxy>(relaxed = true)
            coEvery {
                failingProxy.deliverChannelMessage(any(), any(), any())
            } throws RuntimeException("WebSocket closed")

            val registry = ProxyRegistry()
            registry.register(effortId, member1Name, failingProxy)

            val reactor = makeReactor(eventStore, registry)
            reactor.start()

            // Should not throw despite the delivery exception
            handlerSlot.captured(makeChannelEnvelope(channelEvent))

            // Delivery was attempted
            coVerify(exactly = 1) { failingProxy.deliverChannelMessage(any(), channelEvent, timestamp) }
        }
    }

    describe("MessageDeliveryReactor DM handling") {

        val dmSender    = CollaboratorName.Individual("alice")
        val dmRecipient = CollaboratorName.Individual("dave")

        val dmEvent = DirectMessageEvent.DirectMessagePosted(
            messageId = MessageId("test-dm-001"),
            from      = dmSender,
            body      = "Hi Dave!",
        )

        it("delivers DM to the recipient named in the stream") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore  = mockEventStore(handlerSlot)

            val proxy    = mockk<CollaboratorProxy>(relaxed = true)
            val registry = ProxyRegistry()
            registry.register(effortId, dmRecipient, proxy)

            val reactor = makeReactor(eventStore, registry)
            reactor.start()

            handlerSlot.captured(makeDmEnvelope(dmEvent, dmRecipient))

            val expectedStream = StreamName.DirectMessage(effortId, dmRecipient)
            coVerify(exactly = 1) { proxy.deliverDirectMessage(expectedStream, dmEvent, timestamp) }
        }

        it("silently skips DM when recipient has no registered proxy") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore  = mockEventStore(handlerSlot)
            val registry    = ProxyRegistry()   // no proxies registered

            val reactor = makeReactor(eventStore, registry)
            reactor.start()

            // Should not throw
            handlerSlot.captured(makeDmEnvelope(dmEvent, dmRecipient))
        }
    }

    describe("MessageDeliveryReactor.stop") {

        it("stops the subscription") {
            val mockSubscription = mockk<EventSubscription>(relaxed = true)
            val eventStore = mockk<EventStore>()
            every { eventStore.subscribeToAll(any()) } returns mockSubscription

            val reactor = makeReactor(eventStore)
            reactor.start()
            reactor.stop()

            verify { mockSubscription.stop() }
        }
    }
})
