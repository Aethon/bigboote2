package com.bigboote.domain.aggregates

import com.bigboote.domain.events.AgentEvent
import com.bigboote.domain.events.AgentEvent.*
import com.bigboote.domain.events.LoopEvent
import com.bigboote.domain.events.LoopEvent.*
import com.bigboote.domain.values.*
import kotlinx.datetime.Instant

data class AgentInstanceState(
    val agentTypeId: AgentTypeId,
    val collaboratorName: CollaboratorName,
    val status: AgentStatus,
    val loopStatus: LoopStatus,
    val startedAt: Instant,
) {
    fun apply(event: AgentEvent): AgentInstanceState = when (event) {
        is AgentStarted -> AgentInstanceState(
            agentTypeId = event.agentTypeId,
            collaboratorName = event.collaboratorName,
            status = AgentStatus.STARTED,
            loopStatus = LoopStatus.IDLE,
            startedAt = event.startedAt,
        )
        is AgentStopped -> copy(status = AgentStatus.STOPPED)
        is AgentFailed -> copy(status = AgentStatus.FAILED, loopStatus = LoopStatus.STUCK)
        is AgentPaused -> copy(status = AgentStatus.PAUSED)
        is AgentResumed -> copy(status = AgentStatus.RESUMED)
        is ToolInvoked -> this
        is ToolResultReceived -> this
    }

    fun apply(event: LoopEvent): AgentInstanceState = when (event) {
        is StepStarted -> copy(loopStatus = LoopStatus.IN_STEP)
        is StepEnded -> copy(loopStatus = event.result)
        is AssistantTurnSucceeded -> this
        is AssistantTurnFailed -> this
        is ToolUseRequested -> this
        is ConversationMessageReceived -> this
        is LLMRequestSent -> this
        is LLMResponseReceived -> this
    }

    companion object {
        val EMPTY = AgentInstanceState(
            agentTypeId = AgentTypeId("empty"),
            collaboratorName = CollaboratorName.Individual("__empty__"),
            status = AgentStatus.STARTED,
            loopStatus = LoopStatus.IDLE,
            startedAt = Instant.fromEpochMilliseconds(0),
        )
    }
}
