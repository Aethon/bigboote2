package com.bigboote.coordinator.reactors

import com.bigboote.coordinator.proxy.ProxyRegistry
import com.bigboote.coordinator.projections.repositories.ConversationReadRepository
import com.bigboote.domain.events.ConversationEvent.MessagePosted
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.eventstore.EventSubscription
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(MessageDeliveryReactor::class.java)

/**
 * Reacts to [MessagePosted] events from KurrentDB and fans out delivery
 * to all conversation members via their registered [com.bigboote.coordinator.proxy.CollaboratorProxy].
 *
 * Uses a persistent subscription on `\$all` (group: `message-delivery-reactor`) for
 * at-least-once delivery. Non-[MessagePosted] events are silently ignored.
 *
 * **Delivery flow:**
 * 1. Receive [MessagePosted] via persistent subscription.
 * 2. Load the conversation's member list from the [ConversationReadRepository] Postgres
 *    read model (fast, no KurrentDB replay needed).
 * 3. For each member (excluding the sender):
 *    a. Look up their [com.bigboote.coordinator.proxy.CollaboratorProxy] in [ProxyRegistry].
 *    b. If found, call `proxy.deliverMessage(event)`.
 *
 * Missing proxy registrations are silently skipped (the collaborator may be offline
 * or not yet spawned). Members not in the registry receive no delivery for this event.
 *
 * Phase 12 will complete agent delivery via the SSE gateway subscription.
 * Phase 13 delivers to [com.bigboote.coordinator.proxy.ExternalProxy] (WebSocket) immediately.
 *
 * See Architecture doc Section 7.3.
 */
class MessageDeliveryReactor(
    private val eventStore: EventStore,
    private val proxyRegistry: ProxyRegistry,
    private val conversationReadRepository: ConversationReadRepository,
) {
    private var subscription: EventSubscription? = null

    /**
     * Start the persistent subscription on `\$all`.
     * Called by [ReactorRunner] at coordinator startup.
     */
    fun start() {
        subscription = eventStore.subscribePersistent(
            streamId  = "\$all",
            groupName = "message-delivery-reactor",
        ) { envelope ->
            val event = envelope.data
            if (event is MessagePosted) {
                handleMessagePosted(event)
            }
        }
        logger.info(
            "MessageDeliveryReactor started (persistent subscription on \$all, group=message-delivery-reactor)"
        )
    }

    /**
     * Stop the persistent subscription.
     * Called by [ReactorRunner] on coordinator shutdown.
     */
    fun stop() {
        subscription?.stop()
        subscription = null
        logger.info("MessageDeliveryReactor stopped")
    }

    // ---- private helpers ----

    private suspend fun handleMessagePosted(event: MessagePosted) {
        logger.debug(
            "MessageDeliveryReactor: delivering message {} in conv {} (effort {})",
            event.messageId, event.convId, event.effortId,
        )

        val members = conversationReadRepository.getMembersForConv(event.effortId, event.convId)
        if (members.isEmpty()) {
            logger.debug(
                "MessageDeliveryReactor: no members found for conv {} — skipping delivery",
                event.convId,
            )
            return
        }

        val recipients = members.filter { it != event.from }
        logger.debug(
            "MessageDeliveryReactor: {} recipient(s) for message {} (sender {} excluded)",
            recipients.size, event.messageId, event.from,
        )

        for (recipient in recipients) {
            val proxy = proxyRegistry.get(event.effortId, recipient)
            if (proxy == null) {
                logger.debug(
                    "MessageDeliveryReactor: no proxy for {} in effort {} — skipping",
                    recipient, event.effortId,
                )
                continue
            }
            try {
                proxy.deliverMessage(event)
            } catch (e: Exception) {
                logger.warn(
                    "MessageDeliveryReactor: failed to deliver message {} to {}: {}",
                    event.messageId, recipient, e.message,
                )
            }
        }
    }
}
