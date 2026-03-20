package com.bigboote.domain.aggregates

import com.bigboote.domain.events.AgentEvent.*
import com.bigboote.domain.events.LoopEvent.*
import com.bigboote.domain.values.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock

class AgentInstanceStateTest : StringSpec({

    val now = Clock.System.now()

    val startedEvent = AgentStarted(
        agentTypeId = AgentTypeId.of("lead-eng"),
        collaboratorName = CollaboratorName.Individual("lead-dev"),
        supportedGatewayApiVersions = listOf("v1"),
        supportedControlApiVersions = listOf("v1"),
        startedAt = now,
    )

    "apply AgentStarted initializes state" {
        val state = AgentInstanceState.EMPTY.apply(startedEvent)
        state.status shouldBe AgentStatus.STARTED
        state.loopStatus shouldBe LoopStatus.IDLE
    }

    "apply AgentStopped transitions to STOPPED" {
        val state = AgentInstanceState.EMPTY
            .apply(startedEvent)
            .apply(AgentStopped(now))
        state.status shouldBe AgentStatus.STOPPED
    }

    "apply AgentFailed transitions to FAILED and STUCK" {
        val state = AgentInstanceState.EMPTY
            .apply(startedEvent)
            .apply(AgentFailed("Connection refused", now))
        state.status shouldBe AgentStatus.FAILED
        state.loopStatus shouldBe LoopStatus.STUCK
    }

    "apply AgentPaused transitions to PAUSED" {
        val state = AgentInstanceState.EMPTY
            .apply(startedEvent)
            .apply(AgentPaused(now))
        state.status shouldBe AgentStatus.PAUSED
    }

    "apply AgentResumed transitions to RESUMED" {
        val state = AgentInstanceState.EMPTY
            .apply(startedEvent)
            .apply(AgentPaused(now))
            .apply(AgentResumed(now))
        state.status shouldBe AgentStatus.RESUMED
    }

    "apply StepStarted transitions loopStatus to IN_STEP" {
        val state = AgentInstanceState.EMPTY
            .apply(startedEvent)
            .apply(StepStarted())
        state.loopStatus shouldBe LoopStatus.IN_STEP
    }

    "apply StepEnded sets loopStatus to result" {
        val state = AgentInstanceState.EMPTY
            .apply(startedEvent)
            .apply(StepStarted())
            .apply(StepEnded(LoopStatus.PENDING))
        state.loopStatus shouldBe LoopStatus.PENDING
    }

    "apply StepEnded with IDLE result" {
        val state = AgentInstanceState.EMPTY
            .apply(startedEvent)
            .apply(StepStarted())
            .apply(StepEnded(LoopStatus.IDLE))
        state.loopStatus shouldBe LoopStatus.IDLE
    }

    "LLMRequestSent does not change state" {
        val before = AgentInstanceState.EMPTY.apply(startedEvent)
        val after = before.apply(LLMRequestSent("claude-sonnet-4-6", 1540, now))
        after shouldBe before
    }
})
