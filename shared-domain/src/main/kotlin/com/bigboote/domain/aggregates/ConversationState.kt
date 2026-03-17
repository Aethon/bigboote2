package com.bigboote.domain.aggregates

import com.bigboote.domain.events.ConversationEvent
import com.bigboote.domain.events.ConversationEvent.*
import com.bigboote.domain.values.*
import kotlinx.datetime.Instant

data class ConversationState(
    val convId: String,
    val effortId: EffortId,
    val convName: CollaboratorName,
    val members: List<CollaboratorName>,
    val createdAt: Instant,
) {
    fun apply(event: ConversationEvent): ConversationState = when (event) {
        is ConversationCreated -> ConversationState(
            convId = event.convId,
            effortId = event.effortId,
            convName = event.convName,
            members = event.members,
            createdAt = event.createdAt,
        )
        is MemberAdded -> copy(members = members + event.member)
        is MessagePosted -> this
    }

    companion object {
        val EMPTY = ConversationState(
            convId = "",
            effortId = EffortId("effort:__empty__"),
            convName = CollaboratorName.Channel("__empty__"),
            members = emptyList(),
            createdAt = Instant.fromEpochMilliseconds(0),
        )
    }
}
