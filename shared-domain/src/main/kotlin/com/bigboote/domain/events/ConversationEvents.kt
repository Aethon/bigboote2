package com.bigboote.domain.events

import com.bigboote.domain.values.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ConversationEvent :
Event {

    @Serializable
    @SerialName("ConversationCreated")
    data class ConversationCreated(
        val convId: String,
        val effortId: EffortId,
        val convName: CollaboratorName,
        val members: List<CollaboratorName>,
        val createdAt: Instant,
    ) : ConversationEvent

    @Serializable
    @SerialName("MemberAdded")
    data class MemberAdded(
        val convId: String,
        val effortId: EffortId,
        val member: CollaboratorName,
        val addedAt: Instant,
    ) : ConversationEvent

    @Serializable
    @SerialName("MessagePosted")
    data class MessagePosted(
        val messageId: MessageId,
        val convId: String,
        val effortId: EffortId,
        val from: CollaboratorName,
        val body: String,
        val postedAt: Instant,
    ) : ConversationEvent
}
