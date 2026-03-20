package com.bigboote.coordinator.messaging

import com.bigboote.coordinator.proxy.ExternalProxy
import com.bigboote.domain.events.DirectMessageEvent
import com.bigboote.domain.events.GroupChannelEvent
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.StreamName
import io.ktor.websocket.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(NativeMessagingAdapter::class.java)

/**
 * Creates [ExternalProxy] instances backed by a live WebSocket session.
 *
 * When a human user connects to `WS /api/v1/efforts/{effortId}/messaging`, the
 * route handler calls [createProxy] to obtain an [ExternalProxy] that delivers
 * incoming message events over the WebSocket as JSON text frames.
 *
 * The adapter is a stateless factory — it holds no open sessions itself. Lifecycle
 * (open/close, registry registration/unregistration) is managed by the WebSocket
 * route handler.
 *
 * **WebSocket server-to-client message format** (JSON text frame):
 * ```json
 * {
 *   "type": "message",
 *   "convId": "#review",
 *   "from": "@alice",
 *   "body": "One comment on the token expiry logic.",
 *   "messageId": "V1StGXR8_Z",
 *   "postedAt": "2026-03-16T10:21:00Z"
 * }
 * ```
 * For direct messages, `type` is `"dm"` and `convId` is the sender's handle (e.g. `"@alice"`).
 *
 * **WebSocket client-to-server message format** (JSON text frame):
 * ```json
 * { "convId": "#review", "body": "Here is my response." }
 * ```
 * Client message parsing is handled in [com.bigboote.coordinator.api.public.v1.messagingWebSocketRoutes].
 *
 * See Architecture doc Section 9.1 and API Design doc Section 3.6.
 */
class NativeMessagingAdapter {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    /**
     * Create an [ExternalProxy] bound to [session] for the given collaborator.
     *
     * [deliverChannelMessage] and [deliverDirectMessage] on the returned proxy send
     * [WsDeliveryPayload] JSON text frames over [session]. If the session is closed,
     * the send operation will throw; callers should handle this and unregister the proxy.
     */
    fun createProxy(
        effortId: EffortId,
        collaboratorName: CollaboratorName,
        session: WebSocketSession,
    ): ExternalProxy = object : ExternalProxy {
        override val collaboratorName = collaboratorName
        override val effortId = effortId

        override suspend fun deliverChannelMessage(
            stream: StreamName.GroupChannel,
            event: GroupChannelEvent.ChannelMessagePosted,
            timestamp: Instant,
        ) {
            val payload = WsDeliveryPayload(
                type      = "message",
                convId    = "#${stream.channelName.simple}",
                from      = event.from.toString(),
                body      = event.body,
                messageId = event.messageId.value,
                postedAt  = timestamp.toString(),
            )
            sendPayload(payload, event.messageId.value)
        }

        override suspend fun deliverDirectMessage(
            stream: StreamName.DirectMessage,
            event: DirectMessageEvent.DirectMessagePosted,
            timestamp: Instant,
        ) {
            val payload = WsDeliveryPayload(
                type      = "dm",
                convId    = event.from.toString(),
                from      = event.from.toString(),
                body      = event.body,
                messageId = event.messageId.value,
                postedAt  = timestamp.toString(),
            )
            sendPayload(payload, event.messageId.value)
        }

        private suspend fun sendPayload(payload: WsDeliveryPayload, messageId: String) {
            try {
                session.send(Frame.Text(json.encodeToString(payload)))
                logger.debug(
                    "NativeMessagingAdapter: delivered message {} to {} in effort {}",
                    messageId, collaboratorName, effortId,
                )
            } catch (e: Exception) {
                logger.warn(
                    "NativeMessagingAdapter: failed to deliver message {} to {}: {}",
                    messageId, collaboratorName, e.message,
                )
            }
        }
    }
}

// ---- WebSocket DTOs ----

/**
 * JSON text frame sent server→client for each delivered message event.
 *
 * - `type`: `"message"` for group channel messages, `"dm"` for direct messages.
 * - `convId`: the channel handle (e.g. `"#review"`) or sender handle for DMs (e.g. `"@alice"`).
 */
@Serializable
data class WsDeliveryPayload(
    val type: String,
    val convId: String,
    val from: String,
    val body: String,
    val messageId: String,
    val postedAt: String,
)

/**
 * JSON text frame sent client→server to post a new message.
 * Parsed in [com.bigboote.coordinator.api.public.v1.messagingWebSocketRoutes].
 */
@Serializable
data class WsPostRequest(
    val convId: String,
    val body: String,
)
