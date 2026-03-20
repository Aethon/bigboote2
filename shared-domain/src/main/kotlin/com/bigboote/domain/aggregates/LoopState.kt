package com.bigboote.domain.aggregates

import com.bigboote.domain.events.LoopEvent
import com.bigboote.domain.events.NoContextStreamState
import com.bigboote.domain.values.MessageId
import com.xemantic.ai.anthropic.content.Content
import com.xemantic.ai.anthropic.message.Message

data class LoopState(
    val assistantContext: List<Message>,
    val pendingToolUse: List<Content>,
    val assistantStatus: AssistantStatus,
    val loopStatus: LoopStatus,
    val lastIncludedMessageId: MessageId? = null
) : NoContextStreamState<LoopEvent, LoopState>() {

    override fun apply(event: LoopEvent): LoopState =
        when (event) {
            is LoopEvent.StepStarted -> copy(loopStatus = LoopStatus.IN_STEP)
            is LoopEvent.StepEnded -> copy(loopStatus = event.result)
            is LoopEvent.AssistantTurnSucceeded -> {
                val newContext = assistantContext.toMutableList()
                event.newMessage?.let { newContext.add(it) }
                newContext.add(event.response.asContextMessage())
                return copy(
                    assistantContext = newContext.toList(),
                    assistantStatus = event.assistantStatus,
                    lastIncludedMessageId = event.includedMessageId ?: lastIncludedMessageId
                )
            }

            is LoopEvent.AssistantTurnFailed -> this
            is LoopEvent.ToolUseRequested -> copy(pendingToolUse = pendingToolUse + event.content)

            is LoopEvent.ConversationMessageReceived -> this // TODO: add message to pending content
            is LoopEvent.LLMRequestSent -> TODO()
            is LoopEvent.LLMResponseReceived -> TODO()
        }

    companion object {
        val START = LoopState(
            assistantContext = emptyList(),
            pendingToolUse = emptyList(),
            assistantStatus = AssistantStatus.START,
            loopStatus = LoopStatus.IDLE,
        )
    }
}