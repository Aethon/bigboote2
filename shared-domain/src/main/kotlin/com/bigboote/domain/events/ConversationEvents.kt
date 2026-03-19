package com.bigboote.domain.events

import com.bigboote.domain.values.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Events for the Conversation stream: `/effort:{id}/{convId.streamSafeName}`
 *
 * Both [EffortId] and [ConvId] are inherent to [StreamName.Conversation] and are no
 * longer duplicated in event payloads. Retrieve them via [StreamName.Conversation.effortId]
 * and [StreamName.Conversation.convId] from
 * [com.bigboote.events.eventstore.EventEnvelope.streamName].
 *
 * See Architecture doc Change Document v1.0 Section 5.5.
 */
@Serializable
sealed interface ConversationEvent {

    @Serializable
    @SerialName("ConversationCreated")
    data class ConversationCreated(
        val convName: CollaboratorName,
        val members: List<CollaboratorName>,
        val createdAt: Instant,
    ) : ConversationEvent

    @Serializable
    @SerialName("MemberAdded")
    data class MemberAdded(
        val member: CollaboratorName,
        val addedAt: Instant,
    ) : ConversationEvent

    @Serializable
    @SerialName("MessagePosted")
    data class MessagePosted(
        val messageId: MessageId,
        val from: CollaboratorName,
        val body: String,
        val postedAt: Instant,
    ) : ConversationEvent
}

/**
 * Safely cast an untyped [StreamName] to [StreamName.Conversation].
 * Use this in [EventStore.subscribeToAll] handlers after a type-check on `envelope.data`.
 */
fun StreamName<*>.asConversationStream(): StreamName.Conversation =
    this as? StreamName.Conversation
        ?: error("Expected StreamName.Conversation but got ${this::class.simpleName} for path '$path'")
