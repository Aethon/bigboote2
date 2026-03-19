package com.bigboote.domain.aggregates

import com.bigboote.domain.events.ConversationEvent
import com.bigboote.domain.events.ConversationEvent.*
import com.bigboote.domain.events.Event
import com.bigboote.domain.events.StreamBasedState
import com.bigboote.domain.events.EventContext
import com.bigboote.domain.events.LoopEvent
import com.bigboote.domain.values.*
import com.xemantic.ai.anthropic.message.Message

/** Maintains the current state of all conversations an agent instance is participating in
 */
data class AgentConversationState(
    val agent: CollaboratorName.Individual,
    val conversationIds: Set<String>,
    val pendingMessages: List<Message>,
    override val position: Long?
) : StreamBasedState<Event> {

    override fun applyEvent(event: Event, context: EventContext): AgentConversationState =
        if (event !is ConversationEvent && event !is LoopEvent)
            return
        when (event) {
            is ConversationEvent -> when (event) {
                is ConversationCreated -> {
                    if (!event.members.contains(agent))
                        this
                    else
                        copy(conversationIds = conversationIds + event.convId)
                }

                is MemberAdded -> {
                    if (event.member != agent)
                        this
                    else
                        copy(conversationIds = conversationIds + event.convId)
                }

                is MessagePosted -> {
                    if (!conversationIds.contains(event.convId)) {
                        this
                    } else {
                        copy(pendingMessages = pendingMessages + PendingMessage(event.message, event.timestamp))
                    }
                }
            }
            is LoopEvent ->
        }.copy(position = context.position)

    companion object {
        fun START(agent: CollaboratorName.Individual) = AgentConversationState(agent, emptySet(), emptyList(), null)
    }
}
