package com.bigboote.domain.events

import com.bigboote.domain.values.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Events for the Conversation stream: `/effort:{id}/channel:{channelName}`
 *
 * Note that channelName is presented without the `#` prefix when used in the stream name.
 */
@Serializable
sealed interface GroupChannelEvent :
    Event {

    @Serializable
    @SerialName("ChannelCreated")
    data class ChannelCreated(
        @Contextual
        val members: List<CollaboratorName.Individual>
    ) : GroupChannelEvent

    @Serializable
    @SerialName("MembersAdded")
    data class MembersAdded(
        @Contextual
        val members: Set<CollaboratorName.Individual>
    ) : GroupChannelEvent

    @Serializable
    @SerialName("MessagePosted")
    data class MessagePosted(
        val messageId: MessageId,
        @Contextual
        val from: CollaboratorName.Individual,
        @Contextual
        val to: Set<CollaboratorName.Individual>,
        val body: String,
    ) : GroupChannelEvent
}

/**
 * Safely cast an untyped [StreamName] to [StreamName.Conversation].
 * Use this in [EventStore.subscribeToAll] handlers after a type-check on `envelope.data`.
 */
fun StreamName<*>.asGroupChannelStream(): StreamName.GroupChannel =
    this as? StreamName.GroupChannel
        ?: error("Expected StreamName.GroupChannel but got ${this::class.simpleName} for path '$path'")
