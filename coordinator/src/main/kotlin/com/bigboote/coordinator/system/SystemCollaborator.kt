package com.bigboote.coordinator.system

import com.bigboote.coordinator.aggregates.conversation.ConversationCommandHandler
import com.bigboote.domain.commands.ConversationCommand.PostDirectMessage
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.EffortId
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(SystemCollaborator::class.java)

/**
 * Represents the `@system` built-in collaborator.
 *
 * [SystemCollaborator] is a thin service wrapper that posts direct messages on behalf
 * of `@system` via [ConversationCommandHandler]. It does not hold any state and
 * can be injected wherever system-level notifications are needed.
 *
 * DM streams are created on-demand by [ConversationCommandHandler.handle] (PostDirectMessage)
 * the first time a message is sent — no explicit channel creation step is required.
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
    val name: CollaboratorName.Individual = CollaboratorName.Individual("system")

    /**
     * Send a direct message from `@system` to [toName] in the context of [effortId].
     *
     * The DM is delivered to the stream `StreamName.DirectMessage(effortId, toName)`.
     * The stream is created on-demand — no prior channel setup is needed.
     */
    suspend fun sendDm(effortId: EffortId, toName: CollaboratorName, body: String) {
        val toIndividual = toName as? CollaboratorName.Individual ?: run {
            logger.warn(
                "SystemCollaborator: sendDm target '{}' is not an Individual — skipping",
                toName,
            )
            return
        }

        val cmd = PostDirectMessage(
            effortId = effortId,
            from     = name,
            toName   = toIndividual,
            body     = body,
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
