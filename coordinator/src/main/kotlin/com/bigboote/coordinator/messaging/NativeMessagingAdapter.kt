package com.bigboote.coordinator.messaging

import com.bigboote.coordinator.proxy.ExternalProxy
import com.bigboote.domain.events.ConversationEvent.MessagePosted
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.StreamName
import io.ktor.websocket.*
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
 * incoming [MessagePosted] events over the WebSocket as JSON text frames.
 *
 * The adapter is a stateless factory — it holds no open sessions itself. Lifecycle
 * (open/close, registry registration/unregistration) is managed by the WebSocket
 * route handler.
 *
 * **WebSocket server-to-client message format** (JSON text frame):
 * ```json
 * {
 *   "type": "message",
 *   "convId": "conv:#review",
 *   "from": "@alice",
 *   "body": "One comment on the token expiry logic.",
 *   "messageId": "msg:56",
 *   "postedAt": "2026-03-16T10:21:00Z"
 * }
 * ```
 *
 * **WebSocket client-to-server message format** (JSON text frame):
 * ```json
 * { "convId": "conv:#review", "body": "Here is my response." }
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
     * [deliverMessage] on the returned proxy sends a [WsDeliveryPayload] JSON text
     * frame over [session]. If the session is closed, the send operation will throw
     * a [io.ktor.websocket.ClosedReceiveChannelException]; callers should handle
     * this and unregister the proxy.
     */
    fun createProxy(
        effortId: EffortId,
        collaboratorName: CollaboratorName,
        session: WebSocketSession,
    ): ExternalProxy = object : ExternalProxy {
        override val collaboratorName = collaboratorName
        override val effortId = effortId

        override suspend fun deliverMessage(streamName: StreamName.Conversation, event: MessagePosted) {
            val payload = WsDeliveryPayload(
                type      = "message",
                convId    = streamName.convId.value,
                from      = event.from.toString(),
                body      = event.body,
                messageId = event.messageId.value,
                postedAt  = event.postedAt.toString(),
            )
            try {
                session.send(Frame.Text(json.encodeToString(payload)))
                logger.debug(
                    "NativeMessagingAdapter: delivered message {} to {} in effort {}",
                    event.messageId, collaboratorName, effortId,
                )
            } catch (e: Exception) {
                logger.warn(
                    "NativeMessagingAdapter: failed to deliver message {} to {}: {}",
                    event.messageId, collaboratorName, e.message,
                )
            }
        }
    }
}

// ---- WebSocket DTOs ----

/**
 * JSON text frame sent server→client for each delivered [MessagePosted] event.
 */
@Serializable
data class WsDeliveryPayload(
    val type: String,       // always "message"
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
