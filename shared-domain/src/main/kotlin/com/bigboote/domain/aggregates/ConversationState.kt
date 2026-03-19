package com.bigboote.domain.aggregates

import com.bigboote.domain.events.ConversationEvent
import com.bigboote.domain.events.ConversationEvent.*
import com.bigboote.domain.values.*
import kotlinx.datetime.Instant

data class ConversationState(
    val convName: CollaboratorName,
    val members: List<CollaboratorName>,
    val createdAt: Instant
) : NoContextStreamState<ConversationEvent, ConversationState>() {
    override fun apply(event: ConversationEvent): ConversationState {
        return when (event) {
            is ConversationCreated -> throw IllegalStateException("Conversation already created")
            is MemberAdded -> copy(members = members + event.member)
            is MessagePosted -> this
        }
    }

    companion object: StreamStateStarter<ConversationEvent, ConversationState> {
        override fun start(entry: EventLogEntry<ConversationEvent>): ConversationState {
            val event = entry.event as? ConversationCreated
                ?: throw IllegalArgumentException("Must start with ConversationCreated event")
            return ConversationState(
                convName = event.convName,
                members = event.members,
                createdAt = event.createdAt
            )
        }
    }
}
