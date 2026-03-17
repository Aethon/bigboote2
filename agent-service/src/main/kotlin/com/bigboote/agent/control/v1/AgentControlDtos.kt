package com.bigboote.agent.control.v1

import com.bigboote.domain.values.AgentId
import com.bigboote.domain.values.AgentTypeId
import com.bigboote.domain.values.EffortId
import kotlinx.serialization.Serializable

/**
 * Request body for POST /control/v1/start.
 * Tokens (BIGBOOTE_GATEWAY_TOKEN, BIGBOOTE_AGENT_TOKEN) are already
 * in the container environment --- they are not transmitted in this request.
 */
@Serializable
data class StartRequest(
    val effortId: EffortId,
    val instanceId: AgentId,
    val agentTypeId: AgentTypeId,
    val agentGatewayUrl: String,
)

/** Response for POST /control/v1/start. */
@Serializable
data class StartResponse(
    val started: Boolean,
    val instanceId: AgentId,
)

/**
 * Response for GET /control/v1/status.
 * loopState values: starting | running | paused | stopping | stopped | failed
 */
@Serializable
data class StatusResponse(
    val instanceId: AgentId,
    val effortId: EffortId,
    val agentTypeId: AgentTypeId,
    val loopState: String,
    val currentTurn: Int,
    val lastActivityAt: String?,
    val streamPosition: Long,
)

/** Generic acknowledgement for pause/resume/stop. */
@Serializable
data class AckResponse(
    val success: Boolean,
    val instanceId: AgentId,
)

/** Standard error envelope matching the API Design doc Section 6. */
@Serializable
data class ApiError(
    val error: ErrorDetail,
)

@Serializable
data class ErrorDetail(
    val code: String,
    val message: String,
    val detail: String? = null,
)
