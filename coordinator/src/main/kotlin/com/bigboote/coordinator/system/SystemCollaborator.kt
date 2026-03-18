package com.bigboote.coordinator.system

import com.bigboote.coordinator.aggregates.conversation.ConversationCommandHandler
import com.bigboote.domain.commands.ConversationCommand.PostMessage
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.ConvId
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.MessageId
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(SystemCollaborator::class.java)

/**
 * Represents the `@system` built-in collaborator.
 *
 * [SystemCollaborator] is a thin service wrapper that posts messages on behalf
 * of `@system` via [ConversationCommandHandler]. It does not hold any state and
 * can be injected wherever system-level notifications are needed.
 *
 * DM conversations between `@system` and a target collaborator are created
 * on-demand by [ConversationCommandHandler.handle] (PostMessage) the first time
 * a message is sent — no explicit channel creation step is required.
 *
 * Used by [com.bigboote.coordinator.reactors.SystemMessageReactor] to notify
 * collaborators of effort lifecycle events.
 *
 * See Architecture doc Section 15.
 */
class SystemCollaborator(
    private val conversationCommandHandler: ConversationCommandHandler,
) {

    /** The canonical `@system` collaborator name. */
    val name: CollaboratorName = CollaboratorName.Individual("system")

    /**
     * Send a direct message from `@system` to [toName] in the context of [effortId].
     *
     * Uses [ConvId.dm] for alphabetically-sorted party ordering, which ensures a
     * single canonical DM stream regardless of which party initiates.
     *
     * The DM conversation is auto-created by [ConversationCommandHandler] if it
     * does not yet exist.
     */
    suspend fun sendDm(effortId: EffortId, toName: CollaboratorName, body: String) {
        val convId = ConvId.dm(name.simple, toName.simple).value

        val cmd = PostMessage(
            messageId = MessageId.generate(),
            convId    = convId,
            effortId  = effortId,
            from      = name,
            body      = body,
        )

        try {
            conversationCommandHandler.handle(cmd)
            logger.debug(
                "SystemCollaborator: DM sent to {} in effort {}: {}",
                toName, effortId, body,
            )
        } catch (e: Exception) {
            logger.warn(
                "SystemCollaborator: failed to send DM to {} in effort {}: {}",
                toName, effortId, e.message,
            )
        }
    }
}
