package com.bigboote.domain.events

import com.bigboote.domain.values.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
/**
 * Events for the Conversation stream: `/effort:{id}/dm:{collaboratorName}`
 *
 * Only individuals can be direct messages.
 * Note that collaboratorName is presented without the `@` prefix when used in the stream name.
 */
@Serializable
sealed interface DirectMessageEvent :
    Event {

    @Serializable
    @SerialName("MessagePosted")
    data class MessagePosted(
        val messageId: MessageId,
        @Contextual
        val from: CollaboratorName.Individual,
        val body: String
    ) : DirectMessageEvent

}

/**
 * Safely cast an untyped [StreamName] to [StreamName.Conversation].
 * Use this in [EventStore.subscribeToAll] handlers after a type-check on `envelope.data`.
 */
fun StreamName<*>.asDirectMessageStream(): StreamName.DirectMessage =
    this as? StreamName.DirectMessage
        ?: error("Expected StreamName.DirectMessage but got ${this::class.simpleName} for path '$path'")
