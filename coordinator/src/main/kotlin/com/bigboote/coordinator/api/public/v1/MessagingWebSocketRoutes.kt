package com.bigboote.coordinator.api.public.v1

import com.bigboote.coordinator.aggregates.conversation.ConversationCommandHandler
import com.bigboote.coordinator.api.error.ValidationException
import com.bigboote.coordinator.auth.UserPrincipal
import com.bigboote.coordinator.messaging.NativeMessagingAdapter
import com.bigboote.coordinator.messaging.WsPostRequest
import com.bigboote.coordinator.proxy.ProxyRegistry
import com.bigboote.domain.commands.ConversationCommand.PostMessage
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.MessageId
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.bigboote.coordinator.api.public.v1.MessagingWebSocketRoutes")

/**
 * Real-time messaging WebSocket endpoint for human (external) collaborators.
 *
 * WS /api/v1/efforts/{effortId}/messaging
 *
 * Lifecycle:
 * 1. Client connects (must supply `Authorization: Bearer <token>` during handshake
 *    via the standard Ktor WebSocket upgrade flow; the collaborator identity is derived
 *    from the [UserPrincipal] set by the `"public-api"` auth scheme).
 * 2. An [com.bigboote.coordinator.proxy.ExternalProxy] is created via
 *    [NativeMessagingAdapter] and registered in [ProxyRegistry].
 * 3. Incoming text frames are parsed as [WsPostRequest] JSON and dispatched to
 *    [ConversationCommandHandler.handle] as [PostMessage] commands.
 * 4. Outgoing delivery (messages posted by other participants) is pushed by
 *    [com.bigboote.coordinator.reactors.MessageDeliveryReactor] via the proxy.
 * 5. On disconnect (normal or abnormal), the proxy is unregistered from [ProxyRegistry].
 *
 * **Client-to-server frame format** (JSON text):
 * ```json
 * { "convId": "conv:#review", "body": "Here is my response." }
 * ```
 *
 * **Server-to-client frame format** (JSON text, delivered by MessageDeliveryReactor):
 * ```json
 * { "type": "message", "convId": "conv:#review", "from": "@alice",
 *   "body": "...", "messageId": "msg:56", "postedAt": "2026-03-16T10:21:00Z" }
 * ```
 *
 * Requires `Authorization: Bearer <token>` via the `"public-api"` auth scheme.
 *
 * See Architecture doc Section 9.1 and API Design doc Section 3.6.
 */
fun Route.messagingWebSocketRoutes() {
    val messagingAdapter  by inject<NativeMessagingAdapter>()
    val proxyRegistry     by inject<ProxyRegistry>()
    val commandHandler    by inject<ConversationCommandHandler>()

    val json = Json { ignoreUnknownKeys = true }

    webSocket("/efforts/{effortId}/messaging") {
        val effortId = parseWsEffortId(call.parameters["effortId"])

        // Derive collaborator identity from the JWT principal set by the "public-api"
        // auth scheme. In Phase 7 this is the stub dev principal; in production it
        // will come from the validated JWT sub/collaboratorName claims.
        val principal = call.principal<UserPrincipal>()
        if (principal == null) {
            logger.warn("MessagingWebSocket: no UserPrincipal found — closing with 1008")
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
            return@webSocket
        }

        val collaboratorName = try {
            CollaboratorName.from(principal.collaboratorName)
        } catch (e: IllegalArgumentException) {
            logger.warn(
                "MessagingWebSocket: invalid collaboratorName '{}' from principal — closing",
                principal.collaboratorName,
            )
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid collaborator name"))
            return@webSocket
        }

        val proxy = messagingAdapter.createProxy(effortId, collaboratorName, this)
        proxyRegistry.register(effortId, collaboratorName, proxy)
        logger.info("MessagingWebSocket: connected — {} in effort {}", collaboratorName, effortId)

        try {
            // The for loop terminates naturally when the client disconnects or the
            // channel is closed (normal or abnormal close). Any remaining exceptions
            // bubble up to the outer try/finally for cleanup.
            for (frame in incoming) {
                if (frame !is Frame.Text) continue

                val text = frame.readText()
                try {
                    val req: WsPostRequest = json.decodeFromString(text)
                    val convId = req.convId.ifBlank {
                        logger.warn(
                            "MessagingWebSocket: blank convId from {} — skipping frame",
                            collaboratorName,
                        )
                        continue
                    }
                    if (req.body.isBlank()) {
                        logger.warn(
                            "MessagingWebSocket: blank body from {} — skipping frame",
                            collaboratorName,
                        )
                        continue
                    }

                    val cmd = PostMessage(
                        messageId = MessageId.generate(),
                        convId    = convId,
                        effortId  = effortId,
                        from      = collaboratorName,
                        body      = req.body.trim(),
                    )
                    commandHandler.handle(cmd)

                    logger.debug(
                        "MessagingWebSocket: posted message from {} in conv {} effort {}",
                        collaboratorName, convId, effortId,
                    )
                } catch (e: Exception) {
                    logger.warn(
                        "MessagingWebSocket: failed to process frame from {}: {}",
                        collaboratorName, e.message,
                    )
                }
            }
        } finally {
            proxyRegistry.unregister(effortId, collaboratorName)
            logger.info(
                "MessagingWebSocket: disconnected — {} in effort {}",
                collaboratorName, effortId,
            )
        }
    }
}

// ---- private helpers ----

private fun parseWsEffortId(raw: String?): EffortId {
    if (raw.isNullOrBlank()) throw ValidationException("Missing effortId path parameter")
    return try {
        EffortId(raw)
    } catch (e: IllegalArgumentException) {
        throw ValidationException("Invalid effortId: '$raw'")
    }
}
