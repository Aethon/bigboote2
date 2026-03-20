package com.bigboote.coordinator.reactors

import com.bigboote.coordinator.proxy.ProxyRegistry
import com.bigboote.domain.events.DirectMessageEvent
import com.bigboote.domain.events.GroupChannelEvent
import com.bigboote.domain.values.StreamName
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.eventstore.EventSubscription
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(MessageDeliveryReactor::class.java)

/**
 * Reacts to [GroupChannelEvent.ChannelMessagePosted] and [DirectMessageEvent.DirectMessagePosted]
 * events from KurrentDB and fans out delivery to all recipients via their registered
 * [com.bigboote.coordinator.proxy.CollaboratorProxy].
 *
 * Uses [EventStore.subscribeToAll] for at-least-once delivery across coordinator restarts.
 * Non-message events are silently ignored.
 *
 * **Channel message delivery flow:**
 * 1. Receive [GroupChannelEvent.ChannelMessagePosted] via $all catch-up subscription.
 * 2. Extract recipients from [GroupChannelEvent.ChannelMessagePosted.to] — the recipient
 *    set is embedded directly in the event (no read-model query needed).
 * 3. For each recipient, look up their proxy in [ProxyRegistry] and call
 *    [com.bigboote.coordinator.proxy.CollaboratorProxy.deliverChannelMessage].
 *
 * **Direct message delivery flow:**
 * 1. Receive [DirectMessageEvent.DirectMessagePosted] via $all catch-up subscription.
 * 2. Extract recipient from [StreamName.DirectMessage.collaboratorName].
 * 3. Look up their proxy in [ProxyRegistry] and call
 *    [com.bigboote.coordinator.proxy.CollaboratorProxy.deliverDirectMessage].
 *
 * Missing proxy registrations are silently skipped (the collaborator may be offline
 * or not yet spawned).
 *
 * See Architecture doc Section 7.3.
 */
class MessageDeliveryReactor(
    private val eventStore: EventStore,
    private val proxyRegistry: ProxyRegistry,
) {
    private var subscription: EventSubscription? = null

    /**
     * Start the $all catch-up subscription.
     * Called by [ReactorRunner] at coordinator startup.
     */
    fun start() {
        subscription = eventStore.subscribeToAll { envelope ->
            when (val event = envelope.data) {
                is GroupChannelEvent.ChannelMessagePosted -> {
                    val stream = envelope.streamName as? StreamName.GroupChannel ?: run {
                        logger.warn(
                            "MessageDeliveryReactor: ChannelMessagePosted on unexpected stream: {}",
                            envelope.streamName.path,
                        )
                        return@subscribeToAll
                    }
                    handleChannelMessage(event, stream, envelope.timestamp)
                }
                is DirectMessageEvent.DirectMessagePosted -> {
                    val stream = envelope.streamName as? StreamName.DirectMessage ?: run {
                        logger.warn(
                            "MessageDeliveryReactor: DirectMessagePosted on unexpected stream: {}",
                            envelope.streamName.path,
                        )
                        return@subscribeToAll
                    }
                    handleDirectMessage(event, stream, envelope.timestamp)
                }
                else -> { /* ignore non-message events */ }
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

    private suspend fun handleChannelMessage(
        event: GroupChannelEvent.ChannelMessagePosted,
        stream: StreamName.GroupChannel,
        timestamp: Instant,
    ) {
        val effortId = stream.effortId
        logger.debug(
            "MessageDeliveryReactor: delivering channel message {} in #{} to {} recipient(s) (effort {})",
            event.messageId, stream.channelName.simple, event.to.size, effortId,
        )

        for (recipient in event.to) {
            val proxy = proxyRegistry.get(effortId, recipient)
            if (proxy == null) {
                logger.debug(
                    "MessageDeliveryReactor: no proxy for {} in effort {} — skipping",
                    recipient, effortId,
                )
                continue
            }
            try {
                proxy.deliverChannelMessage(stream, event, timestamp)
            } catch (e: Exception) {
                logger.warn(
                    "MessageDeliveryReactor: failed to deliver message {} to {}: {}",
                    event.messageId, recipient, e.message,
                )
            }
        }
    }

    private suspend fun handleDirectMessage(
        event: DirectMessageEvent.DirectMessagePosted,
        stream: StreamName.DirectMessage,
        timestamp: Instant,
    ) {
        val effortId = stream.effortId
        val recipient = stream.collaboratorName
        logger.debug(
            "MessageDeliveryReactor: delivering DM {} to @{} (effort {})",
            event.messageId, recipient.simple, effortId,
        )

        val proxy = proxyRegistry.get(effortId, recipient)
        if (proxy == null) {
            logger.debug(
                "MessageDeliveryReactor: no proxy for {} in effort {} — skipping",
                recipient, effortId,
            )
            return
        }
        try {
            proxy.deliverDirectMessage(stream, event, timestamp)
        } catch (e: Exception) {
            logger.warn(
                "MessageDeliveryReactor: failed to deliver DM {} to {}: {}",
                event.messageId, recipient, e.message,
            )
        }
    }
}
