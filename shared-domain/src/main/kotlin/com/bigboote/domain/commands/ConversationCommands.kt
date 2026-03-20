package com.bigboote.domain.commands

import com.bigboote.domain.values.*

sealed interface ConversationCommand {

    data class CreateChannel(
        val effortId: EffortId,
        val channelName: CollaboratorName.Channel,
        val members: List<CollaboratorName>
    ) : ConversationCommand

    data class PostMessage(
        val effortId: EffortId,
        val channelName: CollaboratorName.Channel,
        val from: CollaboratorName.Individual,
        val body: String,
    ) : ConversationCommand

    data class AddMembers(
        val effortId: EffortId,
        val channelName: CollaboratorName.Channel,
        val members: Set<CollaboratorName.Individual>,
    ) : ConversationCommand

    data class PostDirectMessage(
        val effortId: EffortId,
        val from: CollaboratorName.Individual,
        val toName: CollaboratorName.Individual,
        val body: String,
    ) : ConversationCommand
}
