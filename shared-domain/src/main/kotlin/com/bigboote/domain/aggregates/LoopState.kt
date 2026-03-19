package com.bigboote.domain.aggregates

import com.bigboote.domain.events.StreamBasedState
import com.bigboote.domain.events.EventContext
import com.bigboote.domain.events.LoopEvent
import com.xemantic.ai.anthropic.content.Content
import com.xemantic.ai.anthropic.message.Message

data class LoopState(
    val assistantContext: List<Message>,
    val pendingToolUse: List<Content>,
    val assistantStatus: AssistantStatus,
    val loopStatus: LoopStatus,
    override val position: Long?
) : StreamBasedState<LoopEvent> {

    override fun applyEvent(event: LoopEvent, context: EventContext): LoopState =
        when (event) {
            is LoopEvent.StepStarted -> copy(loopStatus = LoopStatus.IN_STEP)
            is LoopEvent.StepEnded -> copy(loopStatus = event.result)
            is LoopEvent.AssistantTurnSucceeded -> {
                val newContext = assistantContext.toMutableList()
                event.newMessage?.let { newContext.add(it) }
                newContext.add(event.response.asContextMessage())
                return copy(
                    assistantContext = newContext.toList(),
                    assistantStatus = event.assistantStatus
                )
            }

            is LoopEvent.AssistantTurnFailed -> this
            is LoopEvent.ToolUseRequested -> copy(pendingToolUse = pendingToolUse + event.content)
            is LoopEvent.ConversationMessageReceived -> this // TODO: add message to pending content
        }.copy(position = context.streamPosition)

    companion object {
        val START = LoopState(
            assistantContext = emptyList(),
            pendingToolUse = emptyList(),
            assistantStatus = AssistantStatus.START,
            loopStatus = LoopStatus.IDLE,
            position = null
        )
    }
}