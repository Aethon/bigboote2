package com.bigboote.agent.loop

import com.bigboote.domain.aggregates.PendingCollaborationMessage
import com.bigboote.domain.events.DirectMessageEvent
import com.bigboote.domain.events.EventLogEntry
import com.bigboote.domain.events.GroupChannelEvent
import com.bigboote.domain.events.GroupChannelEvent.*
import com.bigboote.domain.events.StoreState
import com.bigboote.domain.events.asGroupChannelStream
import com.bigboote.domain.events.maybeCast
import com.bigboote.domain.values.MessageId
import com.xemantic.ai.anthropic.content.Text
import kotlin.collections.plus

/** Maintains the current state of all conversations an agent instance is participating in
 */
data class AgentConversationState(
    val pendingMessages: List<PendingCollaborationMessage>
) : StoreState<AgentConversationState>() {

    override fun apply(entry: EventLogEntry<*>): AgentConversationState {
        entry.maybeCast(DirectMessageEvent::class)?.let { entry -> return applyDirectMessageEvent(entry) }

        entry.maybeCast(GroupChannelEvent::class)?.let { entry -> return applyChannelEvent(entry) }

        // FUTURE: fail
        return this
    }

    fun clearPendingMessages(lastSentMessageId: MessageId?): AgentConversationState {
        if (lastSentMessageId == null)
            return this

        val index = pendingMessages.indexOfFirst { it.id == lastSentMessageId }
        if (index < 0)
            return this

        return copy(pendingMessages = pendingMessages.subList(index + 1, pendingMessages.size))
    }

    private fun applyDirectMessageEvent(entry: EventLogEntry<DirectMessageEvent>): AgentConversationState {
        return when (val event = entry.event) {
            is DirectMessageEvent.DirectMessagePosted -> copy(
                pendingMessages = pendingMessages + PendingCollaborationMessage(
                    event.messageId, event.from, null, listOf(Text(event.body))
                )
            )
        }
    }

    private fun applyChannelEvent(entry: EventLogEntry<GroupChannelEvent>): AgentConversationState {
        val event = entry.event
        val stream = entry.streamName.asGroupChannelStream()
        return when (event) {
            // TODO: potentially introduce the new channel to the agent
            is ChannelCreated -> this
            is MembersAdded -> this

            is GroupChannelEvent.ChannelMessagePosted ->
                copy(
                    pendingMessages = pendingMessages + PendingCollaborationMessage(
                        event.messageId, event.from, stream.channelName, listOf(Text(event.body))
                    )
                )
        }
    }


    companion object {
        val START = AgentConversationState(emptyList())
    }
}
