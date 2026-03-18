package com.bigboote.coordinator.messaging

import com.bigboote.domain.values.EffortId
import com.bigboote.events.eventstore.EventEnvelope
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.serialization.EventRegistry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(SseEventBroadcaster::class.java)

/**
 * Fans KurrentDB catch-up subscription events to SSE clients for the
 * Effort Event Stream (`GET /api/v1/efforts/{effortId}/stream`).
 *
 * Each call to [effortEventFlow] creates a new per-connection catch-up subscription
 * on `\$all`, filtered to streams belonging to the requested Effort (stream IDs that
 * start with the effort's stream prefix, e.g. `/effort:V1StGXR8_Z`). This provides:
 * - **Historical replay**: the client receives all past events from position 0.
 * - **Live delivery**: new events are emitted as they arrive.
 *
 * The subscription is automatically cancelled when the collector's coroutine is
 * cancelled (i.e. when the SSE connection closes), via [awaitClose].
 *
 * [fromPosition] allows an SSE client to resume from a known position using the
 * standard `Last-Event-ID` header value. Defaults to 0 (full history).
 *
 * **SSE data format** (matches API Design doc Section 3.5):
 * ```
 * data: {"streamId":"/effort:100/agent:99","position":7,"type":"LLMResponseReceived",
 *        "timestamp":"2026-03-16T10:16:00Z","data":{...}}
 * ```
 *
 * See Architecture doc Section 7.3 and API Design doc Section 3.5.
 */
class SseEventBroadcaster(private val eventStore: EventStore) {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Returns a [Flow] of JSON-encoded SSE data strings for all events belonging to
     * the given [effortId], starting from [fromPosition].
     *
     * Collect this flow inside a `sse { }` route handler and emit each value as a
     * [io.ktor.server.sse.ServerSentEvent]. The flow never completes on its own —
     * it keeps streaming live events until cancelled.
     */
    fun effortEventFlow(effortId: EffortId, fromPosition: Long = 0L): Flow<String> = callbackFlow {
        val streamPrefix = "/${effortId.value}"
        logger.debug("SseEventBroadcaster: new SSE subscription for effort {} from position {}", effortId, fromPosition)

        val subscription = eventStore.subscribeToStream("\$all", fromVersion = fromPosition) { envelope ->
            if (envelope.streamId.startsWith(streamPrefix)) {
                val json = encodeEnvelope(envelope)
                trySend(json)
            }
        }

        awaitClose {
            logger.debug("SseEventBroadcaster: SSE subscription closed for effort {}", effortId)
            subscription.stop()
        }
    }

    // ---- private helpers ----

    @Suppress("UNCHECKED_CAST")
    private fun encodeEnvelope(envelope: EventEnvelope): String {
        val serializer: KSerializer<Any>? =
            EventRegistry.serializerFor(envelope.eventType) as? KSerializer<Any>

        val dataElement: JsonElement = if (serializer != null) {
            try {
                json.encodeToJsonElement(serializer, envelope.data)
            } catch (e: Exception) {
                logger.warn(
                    "SseEventBroadcaster: failed to serialize event {} data: {}",
                    envelope.eventType, e.message,
                )
                buildJsonObject {}
            }
        } else {
            buildJsonObject {}
        }

        return buildJsonObject {
            put("streamId",  envelope.streamId)
            put("position",  envelope.position)
            put("type",      envelope.eventType)
            put("timestamp", envelope.timestamp.toString())
            put("data",      dataElement)
        }.toString()
    }
}
