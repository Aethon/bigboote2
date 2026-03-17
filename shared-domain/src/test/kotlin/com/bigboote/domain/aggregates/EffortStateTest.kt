package com.bigboote.domain.aggregates

import com.bigboote.domain.events.EffortEvent.*
import com.bigboote.domain.values.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock

class EffortStateTest : StringSpec({

    val now = Clock.System.now()
    val effortId = EffortId("effort:test123")
    val lead = CollaboratorName.Individual("lead-dev")

    val createdEvent = EffortCreated(
        effortId = effortId,
        name = "Test Effort",
        goal = "Build something",
        collaborators = listOf(
            CollaboratorSpec(name = lead, type = CollaboratorType.AGENT, agentTypeId = AgentTypeId.of("lead-eng"), isLead = true),
        ),
        leadName = lead,
        createdAt = now,
    )

    "apply EffortCreated initializes state" {
        val state = EffortState.EMPTY.apply(createdEvent)
        state.effortId shouldBe effortId
        state.name shouldBe "Test Effort"
        state.goal shouldBe "Build something"
        state.status shouldBe EffortStatus.CREATED
        state.leadName shouldBe lead
        state.collaborators.size shouldBe 1
        state.version shouldBe 1
    }

    "apply EffortStarted transitions to ACTIVE" {
        val state = EffortState.EMPTY
            .apply(createdEvent)
            .apply(EffortStarted(effortId, now))
        state.status shouldBe EffortStatus.ACTIVE
        state.version shouldBe 2
    }

    "apply EffortPaused transitions to PAUSED" {
        val state = EffortState.EMPTY
            .apply(createdEvent)
            .apply(EffortStarted(effortId, now))
            .apply(EffortPaused(effortId, now))
        state.status shouldBe EffortStatus.PAUSED
        state.version shouldBe 3
    }

    "apply EffortResumed transitions to ACTIVE not RESUMED" {
        val state = EffortState.EMPTY
            .apply(createdEvent)
            .apply(EffortStarted(effortId, now))
            .apply(EffortPaused(effortId, now))
            .apply(EffortResumed(effortId, now))
        state.status shouldBe EffortStatus.ACTIVE
        state.version shouldBe 4
    }

    "apply EffortClosed transitions to CLOSED" {
        val state = EffortState.EMPTY
            .apply(createdEvent)
            .apply(EffortStarted(effortId, now))
            .apply(EffortClosed(effortId, now))
        state.status shouldBe EffortStatus.CLOSED
        state.version shouldBe 3
    }

    "apply AgentSpawnRequested bumps version but does not change state" {
        val started = EffortState.EMPTY
            .apply(createdEvent)
            .apply(EffortStarted(effortId, now))
        val afterSpawn = started.apply(
            AgentSpawnRequested(
                agentId = AgentId("agent:spawn1"),
                effortId = effortId,
                agentTypeId = AgentTypeId.of("lead-eng"),
                collaboratorName = lead,
                gatewayToken = "gw-token",
                agentToken = "agent-token",
                requestedAt = now,
            )
        )
        afterSpawn.status shouldBe EffortStatus.ACTIVE
        afterSpawn.name shouldBe started.name
        afterSpawn.version shouldBe started.version + 1
    }

    "full lifecycle: CREATED -> ACTIVE -> PAUSED -> ACTIVE -> CLOSED" {
        val events = listOf(
            createdEvent,
            EffortStarted(effortId, now),
            EffortPaused(effortId, now),
            EffortResumed(effortId, now),
            EffortClosed(effortId, now),
        )
        val finalState = events.fold(EffortState.EMPTY) { state, event -> state.apply(event) }
        finalState.status shouldBe EffortStatus.CLOSED
        finalState.version shouldBe 5
    }
})
