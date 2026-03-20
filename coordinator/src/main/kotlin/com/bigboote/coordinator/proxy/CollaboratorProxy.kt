package com.bigboote.coordinator.proxy

import com.bigboote.domain.events.DirectMessageEvent
import com.bigboote.domain.events.GroupChannelEvent
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.StreamName
import kotlinx.datetime.Instant

/**
 * Base abstraction over all Collaborator types in an Effort.
 *
 * Every participant in an Effort — human or agent — is represented by a
 * [CollaboratorProxy]. The [com.bigboote.coordinator.reactors.MessageDeliveryReactor]
 * fans out message events to all member proxies by calling [deliverChannelMessage]
 * or [deliverDirectMessage].
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
     * Deliver a [GroupChannelEvent.ChannelMessagePosted] event to this collaborator.
     *
     * For [com.bigboote.coordinator.proxy.agent.AgentProxy]: delivery via the agent's
     * SSE gateway subscription.
     * For [ExternalProxy]: sends the message JSON frame over the collaborator's WebSocket.
     */
    suspend fun deliverChannelMessage(
        stream: StreamName.GroupChannel,
        event: GroupChannelEvent.ChannelMessagePosted,
        timestamp: Instant,
    )

    /**
     * Deliver a [DirectMessageEvent.DirectMessagePosted] event to this collaborator.
     *
     * For [com.bigboote.coordinator.proxy.agent.AgentProxy]: delivery via the agent's
     * SSE gateway subscription.
     * For [ExternalProxy]: sends the message JSON frame over the collaborator's WebSocket.
     */
    suspend fun deliverDirectMessage(
        stream: StreamName.DirectMessage,
        event: DirectMessageEvent.DirectMessagePosted,
        timestamp: Instant,
    )
}
