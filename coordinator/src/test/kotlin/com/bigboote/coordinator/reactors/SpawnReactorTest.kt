package com.bigboote.coordinator.reactors

import com.bigboote.coordinator.proxy.ProxyRegistry
import com.bigboote.coordinator.proxy.agent.AgentProxy
import com.bigboote.coordinator.proxy.agent.AgentStartResponse
import com.bigboote.coordinator.proxy.spawn.SpawnConfig
import com.bigboote.coordinator.proxy.spawn.SpawnStrategy
import com.bigboote.coordinator.projections.repositories.AgentTypeReadRepository
import com.bigboote.coordinator.projections.repositories.AgentTypeRow
import com.bigboote.domain.events.EffortEvent.AgentSpawnRequested
import com.bigboote.domain.values.*
import com.bigboote.events.eventstore.EventEnvelope
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.eventstore.EventSubscription
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.datetime.Instant

/**
 * Unit tests for [SpawnReactor].
 *
 * Verifies that the reactor correctly:
 *  - Subscribes to the \$all stream on start via [EventStore.subscribeToAll].
 *  - Ignores non-[AgentSpawnRequested] events.
 *  - Looks up the AgentType from the read repository.
 *  - Logs and returns early when the AgentType is not found.
 *  - Calls [SpawnStrategy.spawn] with the correct [SpawnConfig].
 *  - Registers the returned proxy in [ProxyRegistry].
 *  - Calls [AgentProxy.start] with the correct parameters.
 *  - Handles spawn failures gracefully (logs, does not crash).
 *  - Stops the subscription on [SpawnReactor.stop].
 *
 * [EffortId] is extracted from [EventEnvelope.streamName] (via [StreamName.Effort]),
 * not from the event payload — consistent with the stream-names change.
 *
 * No real KurrentDB, Postgres, or Docker connections are required.
 */
class SpawnReactorTest : DescribeSpec({

    // ---- fixtures ----

    val agentId     = AgentId("agent:test-agent-001")
    val effortId    = EffortId("effort:test-effort-001")
    val agentTypeId = AgentTypeId("test-agent-type")
    val collab      = CollaboratorName.Individual("test-agent")
    val gatewayUrl  = "http://coordinator-test/internal/v1"

    val spawnEvent = AgentSpawnRequested(
        agentId          = agentId,
        agentTypeId      = agentTypeId,
        collaboratorName = collab,
        gatewayToken     = "gw-token-abc",
        agentToken       = "agent-token-xyz",
        requestedAt      = Instant.fromEpochMilliseconds(0),
    )

    val agentTypeRow = AgentTypeRow(
        agentTypeId   = agentTypeId,
        name          = "Test Agent",
        model         = "claude-opus-4-5",
        systemPrompt  = "You are a test agent.",
        maxTokens     = 1024,
        temperature   = 0.7,
        tools         = emptyList(),
        dockerImage   = "bigboote/agent-service:test",
        spawnStrategy = "docker",
        createdAt     = Instant.fromEpochMilliseconds(0),
        updatedAt     = null,
    )

    // ---- helpers ----

    /** Create a mock EventStore that captures the handler passed to subscribeToAll. */
    fun mockEventStore(captureSlot: CapturingSlot<suspend (EventEnvelope<Any>) -> Unit>): EventStore {
        val mockSubscription = mockk<EventSubscription>(relaxed = true)
        val eventStore = mockk<EventStore>()
        every {
            eventStore.subscribeToAll(capture(captureSlot))
        } returns mockSubscription
        return eventStore
    }

    /**
     * Builds an envelope whose [streamName] is [StreamName.Effort]([effortId]),
     * so the reactor can extract the effort context via [asEffortStream].
     */
    fun makeEnvelope(data: Any): EventEnvelope<Any> = EventEnvelope(
        streamName = StreamName.Effort(effortId),
        eventType  = data::class.simpleName ?: "Unknown",
        position   = 0L,
        data       = data,
        timestamp  = Instant.fromEpochMilliseconds(0),
    )

    // ---- tests ----

    describe("SpawnReactor.start") {

        it("subscribes to \$all on start") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore = mockEventStore(handlerSlot)
            val reactor = SpawnReactor(
                eventStore              = eventStore,
                agentTypeReadRepository = mockk(relaxed = true),
                spawnStrategy           = mockk(relaxed = true),
                proxyRegistry           = ProxyRegistry(),
                coordinatorGatewayUrl   = gatewayUrl,
            )

            reactor.start()

            verify { eventStore.subscribeToAll(any()) }
        }
    }

    describe("SpawnReactor event handling") {

        it("ignores events that are not AgentSpawnRequested") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore = mockEventStore(handlerSlot)
            val agentTypeRepo = mockk<AgentTypeReadRepository>()
            val spawnStrategy = mockk<SpawnStrategy>()

            val reactor = SpawnReactor(
                eventStore              = eventStore,
                agentTypeReadRepository = agentTypeRepo,
                spawnStrategy           = spawnStrategy,
                proxyRegistry           = ProxyRegistry(),
                coordinatorGatewayUrl   = gatewayUrl,
            )
            reactor.start()

            // Send a non-spawn event
            handlerSlot.captured(makeEnvelope("some-other-event"))

            coVerify(exactly = 0) { agentTypeRepo.get(any()) }
            coVerify(exactly = 0) { spawnStrategy.spawn(any()) }
        }

        it("looks up AgentType and calls spawn when AgentSpawnRequested is received") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore = mockEventStore(handlerSlot)

            val agentTypeRepo = mockk<AgentTypeReadRepository>()
            coEvery { agentTypeRepo.get(agentTypeId) } returns agentTypeRow

            val mockProxy = mockk<AgentProxy>()
            every { mockProxy.agentId } returns agentId
            coEvery {
                mockProxy.start(effortId, agentTypeId, gatewayUrl)
            } returns AgentStartResponse(started = true, instanceId = agentId)

            val spawnStrategy = mockk<SpawnStrategy>()
            coEvery { spawnStrategy.spawn(any()) } returns mockProxy

            val registry = ProxyRegistry()

            val reactor = SpawnReactor(
                eventStore              = eventStore,
                agentTypeReadRepository = agentTypeRepo,
                spawnStrategy           = spawnStrategy,
                proxyRegistry           = registry,
                coordinatorGatewayUrl   = gatewayUrl,
            )
            reactor.start()

            handlerSlot.captured(makeEnvelope(spawnEvent))

            // Verify spawn was called with correct SpawnConfig
            coVerify {
                spawnStrategy.spawn(
                    SpawnConfig(
                        agentId          = agentId,
                        effortId         = effortId,
                        agentTypeId      = agentTypeId,
                        collaboratorName = collab,
                        gatewayToken     = "gw-token-abc",
                        agentToken       = "agent-token-xyz",
                        dockerImage      = "bigboote/agent-service:test",
                    )
                )
            }

            // Verify proxy was registered
            registry.get(effortId, collab) shouldBe mockProxy

            // Verify proxy.start was called with the gateway URL
            coVerify { mockProxy.start(effortId, agentTypeId, gatewayUrl) }
        }

        it("logs and skips when AgentType is not found") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore = mockEventStore(handlerSlot)

            val agentTypeRepo = mockk<AgentTypeReadRepository>()
            coEvery { agentTypeRepo.get(agentTypeId) } returns null  // not found

            val spawnStrategy = mockk<SpawnStrategy>()

            val reactor = SpawnReactor(
                eventStore              = eventStore,
                agentTypeReadRepository = agentTypeRepo,
                spawnStrategy           = spawnStrategy,
                proxyRegistry           = ProxyRegistry(),
                coordinatorGatewayUrl   = gatewayUrl,
            )
            reactor.start()

            // Should not throw; spawn should not be called
            handlerSlot.captured(makeEnvelope(spawnEvent))

            coVerify(exactly = 0) { spawnStrategy.spawn(any()) }
        }

        it("handles spawn failures without crashing the reactor") {
            val handlerSlot = slot<suspend (EventEnvelope<Any>) -> Unit>()
            val eventStore = mockEventStore(handlerSlot)

            val agentTypeRepo = mockk<AgentTypeReadRepository>()
            coEvery { agentTypeRepo.get(agentTypeId) } returns agentTypeRow

            val spawnStrategy = mockk<SpawnStrategy>()
            coEvery { spawnStrategy.spawn(any()) } throws RuntimeException("docker run failed (exit 1)")

            val reactor = SpawnReactor(
                eventStore              = eventStore,
                agentTypeReadRepository = agentTypeRepo,
                spawnStrategy           = spawnStrategy,
                proxyRegistry           = ProxyRegistry(),
                coordinatorGatewayUrl   = gatewayUrl,
            )
            reactor.start()

            // Should not throw despite the RuntimeException from spawn
            handlerSlot.captured(makeEnvelope(spawnEvent))

            // No assertion needed — test passes if no exception is thrown
        }
    }

    describe("SpawnReactor.stop") {

        it("stops the subscription") {
            val mockSubscription = mockk<EventSubscription>(relaxed = true)
            val eventStore = mockk<EventStore>()
            every { eventStore.subscribeToAll(any()) } returns mockSubscription

            val reactor = SpawnReactor(
                eventStore              = eventStore,
                agentTypeReadRepository = mockk(relaxed = true),
                spawnStrategy           = mockk(relaxed = true),
                proxyRegistry           = ProxyRegistry(),
                coordinatorGatewayUrl   = gatewayUrl,
            )
            reactor.start()
            reactor.stop()

            verify { mockSubscription.stop() }
        }
    }
})
