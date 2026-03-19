package com.bigboote.domain.aggregates

import com.bigboote.domain.events.ConversationEvent
import com.bigboote.domain.events.ConversationEvent.*
import com.bigboote.domain.values.*
import kotlinx.datetime.Instant

data class ConversationState(
    val convName: CollaboratorName,
    val members: List<CollaboratorName>,
    val createdAt: Instant,
) {
    fun apply(event: ConversationEvent): ConversationState = when (event) {
        is ConversationCreated -> ConversationState(
            convName = event.convName,
            members = event.members,
            createdAt = event.createdAt,
        )
        is MemberAdded -> copy(members = members + event.member)
        is MessagePosted -> this
    }

    companion object {
        val EMPTY = ConversationState(
            convName = CollaboratorName.Channel("__empty__"),
            members = emptyList(),
            createdAt = Instant.fromEpochMilliseconds(0),
        )
    }
}
