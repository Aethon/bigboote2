package com.bigboote.coordinator.proxy.agent

import com.bigboote.domain.events.DirectMessageEvent
import com.bigboote.domain.events.GroupChannelEvent
import com.bigboote.domain.values.AgentId
import com.bigboote.domain.values.AgentTypeId
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.StreamName
import kotlinx.datetime.Instant
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(FlyMachineAgentProxy::class.java)

/**
 * [AgentProxy] implementation that communicates with an agent running on a Fly.io
 * Machine via HTTP calls to its /control/v1/ endpoints on the Fly private network.
 *
 * The control URL follows the Fly.io internal DNS convention:
 *   `http://{machineId}.vm.{appName}.internal:8081/control/v1`
 *
 * Every request carries the per-instance [agentToken] as `X-Agent-Token`, matching
 * the agent-service's auth scheme from Phase 8.  This is identical to
 * [DockerAgentProxy] — the only difference is the URL origin.
 *
 * The [httpClient] is shared across all proxy instances and provided by Koin via
 * [com.bigboote.coordinator.koin.ProxyModule].
 *
 * See Architecture doc Section 20.2.
 */
class FlyMachineAgentProxy(
    override val agentId: AgentId,
    /** e.g. "http://abc123def.vm.bigboote.internal:8081/control/v1" */
    override val controlUrl: String,
    override val collaboratorName: CollaboratorName,
    override val effortId: EffortId,
    private val agentToken: String,
    private val httpClient: HttpClient,
) : AgentProxy {

    override suspend fun deliverChannelMessage(
        stream: StreamName.GroupChannel,
        event: GroupChannelEvent.ChannelMessagePosted,
        timestamp: Instant,
    ) {
        logger.debug(
            "FlyMachineAgentProxy: deliverChannelMessage stub — agent {} would receive message {} " +
            "in channel #{} (SSE gateway delivery pending)",
            agentId, event.messageId, stream.channelName.simple,
        )
    }

    override suspend fun deliverDirectMessage(
        stream: StreamName.DirectMessage,
        event: DirectMessageEvent.DirectMessagePosted,
        timestamp: Instant,
    ) {
        logger.debug(
            "FlyMachineAgentProxy: deliverDirectMessage stub — agent {} would receive DM {} " +
            "from @{} (SSE gateway delivery pending)",
            agentId, event.messageId, event.from.simple,
        )
    }

    override suspend fun start(
        effortId: EffortId,
        agentTypeId: AgentTypeId,
        agentGatewayUrl: String,
    ): AgentStartResponse {
        logger.debug("FlyMachineAgentProxy: starting agent {} at {}", agentId, controlUrl)
        val response: FlyControlStartResponse = httpClient.post("$controlUrl/start") {
            header("X-Agent-Token", agentToken)
            contentType(ContentType.Application.Json)
            setBody(
                FlyControlStartRequest(
                    effortId        = effortId.value,
                    instanceId      = agentId.value,
                    agentTypeId     = agentTypeId.value,
                    agentGatewayUrl = agentGatewayUrl,
                )
            )
        }.body()
        return AgentStartResponse(started = response.started, instanceId = AgentId(response.instanceId))
    }

    override suspend fun status(): AgentStatusResponse {
        val response: FlyControlStatusResponse = httpClient.get("$controlUrl/status") {
            header("X-Agent-Token", agentToken)
        }.body()
        return AgentStatusResponse(
            instanceId     = AgentId(response.instanceId),
            effortId       = EffortId(response.effortId),
            agentTypeId    = AgentTypeId(response.agentTypeId),
            loopState      = response.loopState,
            currentTurn    = response.currentTurn,
            lastActivityAt = response.lastActivityAt,
            streamPosition = response.streamPosition,
        )
    }

    override suspend fun pause(): AgentAckResponse {
        val response: FlyControlAckResponse = httpClient.post("$controlUrl/pause") {
            header("X-Agent-Token", agentToken)
        }.body()
        return AgentAckResponse(success = response.success, instanceId = AgentId(response.instanceId))
    }

    override suspend fun resume(): AgentAckResponse {
        val response: FlyControlAckResponse = httpClient.post("$controlUrl/resume") {
            header("X-Agent-Token", agentToken)
        }.body()
        return AgentAckResponse(success = response.success, instanceId = AgentId(response.instanceId))
    }

    override suspend fun stop(): AgentAckResponse {
        val response: FlyControlAckResponse = httpClient.post("$controlUrl/stop") {
            header("X-Agent-Token", agentToken)
        }.body()
        return AgentAckResponse(success = response.success, instanceId = AgentId(response.instanceId))
    }
}

// ---- Internal wire DTOs ----------------------------------------------------------------
// Mirrors DockerAgentProxy's internal DTOs.  The coordinator does not depend on the
// agent-service module, so duplicated @Serializable data classes are used here.
// Field names use camelCase defaults to match the agent-service's JSON exactly.

@Serializable
private data class FlyControlStartRequest(
    val effortId: String,
    val instanceId: String,
    val agentTypeId: String,
    val agentGatewayUrl: String,
)

@Serializable
private data class FlyControlStartResponse(
    val started: Boolean,
    val instanceId: String,
)

@Serializable
private data class FlyControlStatusResponse(
    val instanceId: String,
    val effortId: String,
    val agentTypeId: String,
    val loopState: String,
    val currentTurn: Int,
    val lastActivityAt: String? = null,
    val streamPosition: Long,
)

@Serializable
private data class FlyControlAckResponse(
    val success: Boolean,
    val instanceId: String,
)
