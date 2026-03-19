package com.bigboote.coordinator.proxy

import com.bigboote.domain.events.ConversationEvent.MessagePosted
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.StreamName

/**
 * Base abstraction over all Collaborator types in an Effort.
 *
 * Every participant in an Effort — human or agent — is represented by a
 * [CollaboratorProxy]. The [com.bigboote.coordinator.reactors.MessageDeliveryReactor]
 * fans out [MessagePosted] events to all member proxies by calling [deliverMessage].
 *
 * Sub-interfaces:
 * - [com.bigboote.coordinator.proxy.agent.AgentProxy] — agent instance with full control plane
 * - [ExternalProxy] — WebSocket-connected human user (control-plane-free)
 *
 * See Architecture doc Section 9.1.
 */
interface CollaboratorProxy {
    /** Identity of this collaborator as it appears in conversation membership. */
    val collaboratorName: CollaboratorName

    /** The Effort this proxy belongs to. */
    val effortId: EffortId

    /**
     * Deliver a [MessagePosted] event to this collaborator.
     *
     * For [com.bigboote.coordinator.proxy.agent.AgentProxy]: Phase 12 will implement
     * delivery via the agent's SSE gateway subscription.
     * For [ExternalProxy]: sends the message JSON frame over the collaborator's WebSocket.
     */
    suspend fun deliverMessage(streamName: StreamName.Conversation, event: MessagePosted)
}
