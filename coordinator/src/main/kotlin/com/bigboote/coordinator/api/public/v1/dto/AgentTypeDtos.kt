package com.bigboote.coordinator.api.public.v1.dto

import kotlinx.serialization.Serializable

// ---- request bodies ----

/**
 * Nested model parameters in [CreateAgentTypeRequest] and [UpdateAgentTypeRequest].
 * Kept as a sub-object to mirror the wire shape documented in API Design Section 3.2.
 */
@Serializable
data class ModelParamsRequest(
    val maxTokens: Int,
    val temperature: Double? = null,
)

/**
 * Request body for POST /api/v1/agent-types/create.
 *
 * [id] must be a valid [com.bigboote.domain.values.AgentTypeId] value, i.e. it must
 * start with "agenttype:" followed by a lowercase-alphanumeric-with-hyphens slug
 * (e.g. "agenttype:lead-engineer").
 *
 * [tools] is optional — absent or null means the agent type has no tools.
 */
@Serializable
data class CreateAgentTypeRequest(
    val id: String,
    val name: String,
    val model: String,
    val systemPrompt: String,
    val modelParams: ModelParamsRequest,
    val tools: List<String>? = null,
    val dockerImage: String,
    val spawnStrategy: String,
)

/**
 * Request body for POST /api/v1/agent-types/{agentTypeId}/update.
 *
 * All fields are optional — only non-null fields are applied. At least one field
 * should be supplied, though the handler does not enforce this (an all-null update
 * emits an AgentTypeUpdated event with only the updatedAt timestamp changed).
 */
@Serializable
data class UpdateAgentTypeRequest(
    val name: String? = null,
    val model: String? = null,
    val systemPrompt: String? = null,
    val modelParams: ModelParamsRequest? = null,
    val tools: List<String>? = null,
    val dockerImage: String? = null,
    val spawnStrategy: String? = null,
)

// ---- response bodies ----

/** Response for POST /api/v1/agent-types/create (201 Created). */
@Serializable
data class CreateAgentTypeResponse(
    val agentTypeId: String,
    val createdAt: String,
)

/** Response for POST /api/v1/agent-types/{agentTypeId}/update (200 OK). */
@Serializable
data class UpdateAgentTypeResponse(
    val agentTypeId: String,
    val updatedAt: String,
)

/** Detailed AgentType view returned by GET endpoints. */
@Serializable
data class AgentTypeDetailResponse(
    val agentTypeId: String,
    val name: String,
    val model: String,
    val systemPrompt: String,
    val maxTokens: Int,
    val temperature: Double,
    val tools: List<String>,
    val dockerImage: String,
    val spawnStrategy: String,
    val createdAt: String,
    val updatedAt: String?,
)

/** Response for GET /api/v1/agent-types (200 OK). */
@Serializable
data class AgentTypeListResponse(
    val agentTypes: List<AgentTypeDetailResponse>,
)
