package com.bigboote.coordinator.reactors

import com.bigboote.coordinator.proxy.CollaboratorProxy
import com.bigboote.coordinator.proxy.ProxyRegistry
import com.bigboote.coordinator.projections.repositories.ConversationReadRepository
import com.bigboote.domain.events.ConversationEvent.MessagePosted
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.ConvId
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
 *  - Ignores non-[MessagePosted] events silently.
 *  - Loads conversation members from [ConversationReadRepository].
 *  - Skips delivery when no members are found for the conversation.
 *  - Excludes the sender from the delivery recipients list.
 *  - Calls [CollaboratorProxy.deliverMessage] for each recipient that has a registered proxy.
 *  - Silently skips recipients whose proxy is not registered (offline/unspawned).
 *  - Handles delivery failures gracefully (logs, does not crash the reactor).
 *  - Stops the subscription on [MessageDeliveryReactor.stop].
 *
 * [EffortId] and [ConvId] are extracted from [EventEnvelope.streamName]
 * (via [StreamName.Conversation]), not from the event payload — consistent with
 * the stream-names change. [getMembersForConv] is called with [ConvId.value].
 *
 * No real KurrentDB, Postgres, or WebSocket connections are required.
 */
class MessageDeliveryReactorTest : DescribeSpec({

    // ---- fixtures ----

    val effortId     = EffortId("effort:test-001")
    val convId       = ConvId.channel("general")   // value = "conv:#general"
    val senderName   = CollaboratorName.Individual("alice")
    val member1Name  = CollaboratorName.Individual("bob")
    val member2Name  = CollaboratorName.Individual("charlie")

    val messagePostedEvent = MessagePosted(
        messageId = MessageId("msg:test-001"),
        from      = senderName,
        body      = "Hello everyone!",
        postedAt  = Instant.fromEpochMilliseconds(0),
    )

    // ---- helpers ----

    /** Create a mock EventStore that captures the subscription handler. */
    fun mockEventStore(captureSlot: CapturingSlot<suspend (EventEnvelope<Any>) -> Unit>): EventStore {
        val mockSubscription = mockk<EventSubscription>(relaxed = true)
        val eventStore = mockk<EventStore>()
        every {
            eventStore.subscribeToAll(capture(captureSlot))
        } returns mockSubscription
        return eventStore
    }

    /**
     * Builds an envelope with [streamName] = [StreamName.Conversation]([effortId], [convId]),
     * so the reactor can extract effort/conv context via [asConversationStream].
     */
    fun makeEnvelope(data: Any): EventEnvelope<Any> = EventEnvelope(
        streamName = StreamName.Conversation(effortId, convId),
        eventType  = data::class.simpleName ?: "Unknown",
        position   = 0L,
        data       = data,
        timestamp  = Instant.fromEpochMilliseconds(0),
    )

    /** Build a reactor with the provided mocks and a real (empty) ProxyRegistry by default. */
    fun makeReactor(
        eventStore: EventStore,
        readRepo: ConversationReadRepository,
        registry: ProxyRegistry = ProxyRegistry(),
    ) = MessageDeliveryReactor(
        eventStore                 = eventStore,
        proxyRegistry              = registry,
        conversationReadRepository = readRepo,
    )

    // ---- tests ----

    describe("MessageDeliveryReactor.start") {

        it("subscribes to \$all on start") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore  = mockEventStore(handlerSlot)
            val reactor = makeReactor(eventStore, mockk(relaxed = true))

            reactor.start()

            verify { eventStore.subscribeToAll(any()) }
        }
    }

    describe("MessageDeliveryReactor event handling") {

        it("ignores events that are not MessagePosted") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore  = mockEventStore(handlerSlot)
            val readRepo    = mockk<ConversationReadRepository>()
            val reactor     = makeReactor(eventStore, readRepo)
            reactor.start()

            handlerSlot.captured(makeEnvelope("some-other-event"))

            coVerify(exactly = 0) { readRepo.getMembersForConv(any(), any()) }
        }

        it("skips delivery when getMembersForConv returns an empty list") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore  = mockEventStore(handlerSlot)
            val readRepo    = mockk<ConversationReadRepository>()
            // getMembersForConv takes convId as String (convId.value)
            coEvery { readRepo.getMembersForConv(effortId, convId.value) } returns emptyList()

            val registry = mockk<ProxyRegistry>(relaxed = true)
            val reactor  = makeReactor(eventStore, readRepo, registry)
            reactor.start()

            handlerSlot.captured(makeEnvelope(messagePostedEvent))

            // No proxy lookups should happen when there are no members
            verify(exactly = 0) { registry.get(any(), any()) }
        }

        it("delivers to all members except the sender") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore  = mockEventStore(handlerSlot)
            val readRepo    = mockk<ConversationReadRepository>()
            coEvery { readRepo.getMembersForConv(effortId, convId.value) } returns
                listOf(senderName, member1Name, member2Name)

            val proxy1 = mockk<CollaboratorProxy>(relaxed = true)
            val proxy2 = mockk<CollaboratorProxy>(relaxed = true)

            val registry = ProxyRegistry()
            registry.register(effortId, member1Name, proxy1)
            registry.register(effortId, member2Name, proxy2)

            val reactor = makeReactor(eventStore, readRepo, registry)
            reactor.start()

            handlerSlot.captured(makeEnvelope(messagePostedEvent))

            // Sender (alice) must NOT receive the message
            coVerify(exactly = 0) { registry.get(effortId, senderName) }
            // Recipients (bob, charlie) must receive the message
            coVerify(exactly = 1) { proxy1.deliverMessage(messagePostedEvent) }
            coVerify(exactly = 1) { proxy2.deliverMessage(messagePostedEvent) }
        }

        it("silently skips recipients with no registered proxy") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore  = mockEventStore(handlerSlot)
            val readRepo    = mockk<ConversationReadRepository>()
            coEvery { readRepo.getMembersForConv(effortId, convId.value) } returns
                listOf(senderName, member1Name, member2Name)

            // Only member1 has a proxy; member2 is offline / not yet spawned
            val proxy1 = mockk<CollaboratorProxy>(relaxed = true)
            val registry = ProxyRegistry()
            registry.register(effortId, member1Name, proxy1)

            val reactor = makeReactor(eventStore, readRepo, registry)
            reactor.start()

            // Should not throw despite member2 having no proxy
            handlerSlot.captured(makeEnvelope(messagePostedEvent))

            coVerify(exactly = 1) { proxy1.deliverMessage(messagePostedEvent) }
        }

        it("handles deliverMessage failures without crashing the reactor") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore  = mockEventStore(handlerSlot)
            val readRepo    = mockk<ConversationReadRepository>()
            coEvery { readRepo.getMembersForConv(effortId, convId.value) } returns
                listOf(senderName, member1Name)

            val failingProxy = mockk<CollaboratorProxy>(relaxed = true)
            coEvery { failingProxy.deliverMessage(any()) } throws RuntimeException("WebSocket closed")

            val registry = ProxyRegistry()
            registry.register(effortId, member1Name, failingProxy)

            val reactor = makeReactor(eventStore, readRepo, registry)
            reactor.start()

            // Should not throw despite the delivery exception
            handlerSlot.captured(makeEnvelope(messagePostedEvent))

            // Delivery was attempted
            coVerify(exactly = 1) { failingProxy.deliverMessage(messagePostedEvent) }
        }
    }

    describe("MessageDeliveryReactor.stop") {

        it("stops the subscription") {
            val mockSubscription = mockk<EventSubscription>(relaxed = true)
            val eventStore = mockk<EventStore>()
            every { eventStore.subscribeToAll(any()) } returns mockSubscription

            val reactor = makeReactor(eventStore, mockk(relaxed = true))
            reactor.start()
            reactor.stop()

            verify { mockSubscription.stop() }
        }
    }
})
