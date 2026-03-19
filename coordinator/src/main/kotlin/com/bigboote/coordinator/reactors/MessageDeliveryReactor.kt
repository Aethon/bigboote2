package com.bigboote.coordinator.reactors

import com.bigboote.coordinator.proxy.ProxyRegistry
import com.bigboote.coordinator.projections.repositories.ConversationReadRepository
import com.bigboote.domain.events.ConversationEvent.MessagePosted
import com.bigboote.domain.events.asConversationStream
import com.bigboote.domain.values.ConvId
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.StreamName
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.eventstore.EventSubscription
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(MessageDeliveryReactor::class.java)

/**
 * Reacts to [MessagePosted] events from KurrentDB and fans out delivery
 * to all conversation members via their registered [com.bigboote.coordinator.proxy.CollaboratorProxy].
 *
 * Uses [EventStore.subscribeToAll] for at-least-once delivery across coordinator
 * restarts. Non-[MessagePosted] events are silently ignored.
 *
 * **Delivery flow:**
 * 1. Receive [MessagePosted] via \$all catch-up subscription.
 * 2. Extract [EffortId] and [ConvId] from
 *    [envelope.streamName][com.bigboote.events.eventstore.EventEnvelope.streamName]
 *    via [asConversationStream].
 * 3. Load the conversation's member list from the [ConversationReadRepository] Postgres
 *    read model (fast, no KurrentDB replay needed).
 * 4. For each member (excluding the sender):
 *    a. Look up their [com.bigboote.coordinator.proxy.CollaboratorProxy] in [ProxyRegistry].
 *    b. If found, call `proxy.deliverMessage(event)`.
 *
 * Missing proxy registrations are silently skipped (the collaborator may be offline
 * or not yet spawned). Members not in the registry receive no delivery for this event.
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
     * Start the \$all catch-up subscription.
     * Called by [ReactorRunner] at coordinator startup.
     */
    fun start() {
        subscription = eventStore.subscribeToAll { envelope ->
            val event = envelope.data
            if (event is MessagePosted) {
                val stream = envelope.streamName.asConversationStream()
                handleMessagePosted(event, stream.effortId, stream.convId)
            }
        }
        logger.info("MessageDeliveryReactor started (\$all catch-up subscription)")
    }

    /**
     * Stop the subscription.
     * Called by [ReactorRunner] on coordinator shutdown.
     */
    fun stop() {
        subscription?.stop()
        subscription = null
        logger.info("MessageDeliveryReactor stopped")
    }

    // ---- private helpers ----

    private suspend fun handleMessagePosted(
        event: MessagePosted,
        effortId: EffortId,
        convId: ConvId,
    ) {
        logger.debug(
            "MessageDeliveryReactor: delivering message {} in conv {} (effort {})",
            event.messageId, convId, effortId,
        )

        val members = conversationReadRepository.getMembersForConv(effortId, convId.value)
        if (members.isEmpty()) {
            logger.debug(
                "MessageDeliveryReactor: no members found for conv {} — skipping delivery",
                convId,
            )
            return
        }

        val recipients = members.filter { it != event.from }
        logger.debug(
            "MessageDeliveryReactor: {} recipient(s) for message {} (sender {} excluded)",
            recipients.size, event.messageId, event.from,
        )

        for (recipient in recipients) {
            val proxy = proxyRegistry.get(effortId, recipient)
            if (proxy == null) {
                logger.debug(
                    "MessageDeliveryReactor: no proxy for {} in effort {} — skipping",
                    recipient, effortId,
                )
                continue
            }
            try {
                proxy.deliverMessage(StreamName.Conversation(effortId, convId), event)
            } catch (e: Exception) {
                logger.warn(
                    "MessageDeliveryReactor: failed to deliver message {} to {}: {}",
                    event.messageId, recipient, e.message,
                )
            }
        }
    }
}
