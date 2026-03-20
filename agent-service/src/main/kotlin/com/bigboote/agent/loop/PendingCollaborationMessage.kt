package com.bigboote.domain.aggregates

import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.MessageId
import com.xemantic.ai.anthropic.content.Content

/**
 * Describes a collaboration message waiting to be sent to the assistant.
 *
 * @property id The unique identifier of the message.
 * @property from The name of the sender.
 * @property to The `#`-prefixed name of the channel the message was sent to, or `null` if sent directly.
 * @property content The content of the message.
 */
data class PendingCollaborationMessage(
    val id: MessageId,
    val from: CollaboratorName.Individual,
    val to: CollaboratorName.Channel?,
    val content: List<Content>
)