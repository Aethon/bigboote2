package com.bigboote.coordinator.api.public.v1

import com.bigboote.coordinator.api.error.ValidationException
import com.bigboote.coordinator.messaging.SseEventBroadcaster
import com.bigboote.domain.values.EffortId
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.bigboote.coordinator.api.public.v1.SseRoutes")

/**
 * SSE event stream endpoint for an Effort.
 *
 * GET /api/v1/efforts/{effortId}/stream
 *
 * Streams all KurrentDB events belonging to the given Effort as Server-Sent Events.
 * Each SSE `data:` field is a JSON object matching the format documented in
 * [SseEventBroadcaster]:
 * ```
 * data: {"streamId":"/effort:xyz/agent:99","position":7,"type":"LLMResponseReceived",
 *        "timestamp":"2026-03-16T10:16:00Z","data":{...}}
 * ```
 *
 * **Resume support:** if the client sends `Last-Event-ID: <position>` on reconnect,
 * this handler will resume the KurrentDB catch-up subscription from that position,
 * avoiding a full replay. If the header is absent or zero, replay starts from the
 * beginning of the stream.
 *
 * **Heartbeats:** a comment-only event is sent every 30 seconds while the connection
 * is open to prevent proxies from closing idle connections.
 *
 * Requires `Authorization: Bearer <token>` via the `"public-api"` auth scheme.
 *
 * See API Design doc Section 3.5 and Architecture doc Section 7.3.
 */
fun Route.sseRoutes() {
    val broadcaster by application.inject<SseEventBroadcaster>()

    sse("/efforts/{effortId}/stream") {
        val effortId = parseSseEffortId(call.parameters["effortId"])
        val fromPosition = call.request.headers["Last-Event-ID"]?.toLongOrNull() ?: 0L

        logger.info("SSE client connected for effort {} from position {}", effortId, fromPosition)

        // Launch a heartbeat coroutine — sends a comment every 30 s to keep the
        // connection alive through proxies and load balancers that close idle sockets.
        val heartbeatJob = launch {
            while (true) {
                delay(30_000)
                send(ServerSentEvent(comments = "heartbeat"))
            }
        }

        try {
            broadcaster.effortEventFlow(effortId, fromPosition).collect { eventData ->
                send(ServerSentEvent(data = eventData))
            }
        } finally {
            heartbeatJob.cancel()
            logger.info("SSE client disconnected for effort {}", effortId)
        }
    }
}

// ---- private helpers ----

private fun parseSseEffortId(raw: String?): EffortId {
    if (raw.isNullOrBlank()) throw ValidationException("Missing effortId path parameter")
    return try {
        EffortId(raw)
    } catch (e: IllegalArgumentException) {
        throw ValidationException("Invalid effortId: '$raw'")
    }
}
