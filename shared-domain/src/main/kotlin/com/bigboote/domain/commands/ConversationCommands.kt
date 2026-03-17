package com.bigboote.domain.commands

import com.bigboote.domain.values.*

sealed interface ConversationCommand {

    data class CreateChannel(
        val convId: ConvId,
        val effortId: EffortId,
        val convName: CollaboratorName,
        val members: List<CollaboratorName>,
    ) : ConversationCommand

    data class PostMessage(
        val messageId: MessageId,
        val convId: String?,
        val effortId: EffortId,
        val from: CollaboratorName,
        val body: String,
    ) : ConversationCommand

    data class AddMember(
        val convId: String,
        val effortId: EffortId,
        val member: CollaboratorName,
    ) : ConversationCommand
}
