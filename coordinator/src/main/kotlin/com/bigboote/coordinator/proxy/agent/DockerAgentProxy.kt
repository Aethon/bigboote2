package com.bigboote.coordinator.proxy.agent

import com.bigboote.domain.events.ConversationEvent.MessagePosted
import com.bigboote.domain.values.AgentId
import com.bigboote.domain.values.AgentTypeId
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.StreamName
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(DockerAgentProxy::class.java)

/**
 * [AgentProxy] implementation that communicates with an agent running in a Docker
 * container via HTTP calls to its /control/v1/ endpoints.
 *
 * Every request carries the per-instance [agentToken] as `X-Agent-Token`, matching
 * the agent-service's auth scheme from Phase 8.
 *
 * The [httpClient] is shared across all proxy instances and provided by Koin via
 * [com.bigboote.coordinator.koin.ProxyModule]. It must be configured with
 * ContentNegotiation(JSON) so that [body()] deserialization works.
 *
 * See Architecture doc Section 10.2.
 */
class DockerAgentProxy(
    override val agentId: AgentId,
    override val controlUrl: String,   // e.g. "http://127.0.0.1:49153/control/v1"
    override val collaboratorName: CollaboratorName,
    override val effortId: EffortId,
    private val agentToken: String,
    private val httpClient: HttpClient,
) : AgentProxy {

    /**
     * Phase 12 stub: agent message delivery will be implemented via the coordinator's
     * SSE gateway subscription endpoint (`/internal/v1/agent/{id}/subscribe-conversation-events`).
     * For now, log the delivery attempt so that Phase 13 integration tests can verify
     * [com.bigboote.coordinator.reactors.MessageDeliveryReactor] invokes this method.
     */
    override suspend fun deliverMessage(streamName: StreamName.Conversation, event: MessagePosted) {
        logger.debug(
            "DockerAgentProxy: deliverMessage stub — agent {} would receive message {} " +
            "(Phase 12 SSE gateway delivery pending)",
            agentId, event.messageId,
        )
    }

    override suspend fun start(
        effortId: EffortId,
        agentTypeId: AgentTypeId,
        agentGatewayUrl: String,
    ): AgentStartResponse {
        logger.debug("DockerAgentProxy: starting agent {} at {}", agentId, controlUrl)
        val response: ControlStartResponse = httpClient.post("$controlUrl/start") {
            header("X-Agent-Token", agentToken)
            contentType(ContentType.Application.Json)
            setBody(
                ControlStartRequest(
                    effortId    = effortId.value,
                    instanceId  = agentId.value,
                    agentTypeId = agentTypeId.value,
                    agentGatewayUrl = agentGatewayUrl,
                )
            )
        }.body()
        return AgentStartResponse(started = response.started, instanceId = AgentId(response.instanceId))
    }

    override suspend fun status(): AgentStatusResponse {
        val response: ControlStatusResponse = httpClient.get("$controlUrl/status") {
            header("X-Agent-Token", agentToken)
        }.body()
        return AgentStatusResponse(
            instanceId      = AgentId(response.instanceId),
            effortId        = EffortId(response.effortId),
            agentTypeId     = AgentTypeId(response.agentTypeId),
            loopState       = response.loopState,
            currentTurn     = response.currentTurn,
            lastActivityAt  = response.lastActivityAt,
            streamPosition  = response.streamPosition,
        )
    }

    override suspend fun pause(): AgentAckResponse {
        val response: ControlAckResponse = httpClient.post("$controlUrl/pause") {
            header("X-Agent-Token", agentToken)
        }.body()
        return AgentAckResponse(success = response.success, instanceId = AgentId(response.instanceId))
    }

    override suspend fun resume(): AgentAckResponse {
        val response: ControlAckResponse = httpClient.post("$controlUrl/resume") {
            header("X-Agent-Token", agentToken)
        }.body()
        return AgentAckResponse(success = response.success, instanceId = AgentId(response.instanceId))
    }

    override suspend fun stop(): AgentAckResponse {
        val response: ControlAckResponse = httpClient.post("$controlUrl/stop") {
            header("X-Agent-Token", agentToken)
        }.body()
        return AgentAckResponse(success = response.success, instanceId = AgentId(response.instanceId))
    }
}

// ---- Internal wire DTOs ----------------------------------------------------------------
// These mirror the agent-service's AgentControlDtos.kt. The coordinator does not
// depend on the agent-service module, so duplicated @Serializable data classes are
// used here for JSON serialization/deserialization of the Control API payloads.
// Field names must match the agent-service's JSON exactly (snake_case via @SerialName
// is NOT needed here — the agent-service uses camelCase defaults with Kotlinx).

@Serializable
private data class ControlStartRequest(
    val effortId: String,
    val instanceId: String,
    val agentTypeId: String,
    val agentGatewayUrl: String,
)

@Serializable
private data class ControlStartResponse(
    val started: Boolean,
    val instanceId: String,
)

@Serializable
private data class ControlStatusResponse(
    val instanceId: String,
    val effortId: String,
    val agentTypeId: String,
    val loopState: String,
    val currentTurn: Int,
    val lastActivityAt: String? = null,
    val streamPosition: Long,
)

@Serializable
private data class ControlAckResponse(
    val success: Boolean,
    val instanceId: String,
)
