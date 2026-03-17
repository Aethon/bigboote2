package com.bigboote.coordinator.api.public.v1.dto

import com.bigboote.domain.values.CollaboratorType
import kotlinx.serialization.Serializable

/**
 * Request/response DTOs for the Effort Public API.
 *
 * DTOs are intentionally separate from domain types. Collaborator names in the
 * API use bare names without the @ prefix (e.g. "lead-dev" rather than "@lead-dev").
 * Route handlers are responsible for adding the @ prefix when constructing
 * domain [com.bigboote.domain.values.CollaboratorName] values.
 *
 * AgentTypeId in the API uses the full "agenttype:" prefix to match the
 * Event Schema (authoritative per the task brief). E.g. "agenttype:lead-eng".
 *
 * See API Design doc Section 3.1.
 */

// ------------------------------------------------------------------ request DTOs

@Serializable
data class CreateEffortRequest(
    val name: String,
    val goal: String,
    val collaborators: List<CollaboratorSpecRequest>,
)

/**
 * Collaborator specification in a create/update request.
 * [name] is a bare name without @ prefix (e.g. "lead-dev").
 */
@Serializable
data class CollaboratorSpecRequest(
    val name: String,
    val type: CollaboratorType,
    val agentTypeId: String? = null,
    val isLead: Boolean = false,
)

// ------------------------------------------------------------------ response DTOs

@Serializable
data class CreateEffortResponse(
    val effortId: String,
    val status: String,
    val createdAt: String,
)

@Serializable
data class EffortSummaryResponse(
    val effortId: String,
    val name: String,
    val goal: String,
    val status: String,
    val lead: String,                            // bare name without @ (e.g. "lead-dev")
    val collaborators: List<CollaboratorSpecResponse>,
    val createdAt: String,
    val startedAt: String? = null,
    val closedAt: String? = null,
)

/**
 * Collaborator in a response.
 * [name] is a bare name without @ prefix.
 * [instanceId] and [instanceStatus] are populated in Phase 10+ when agents are spawned.
 */
@Serializable
data class CollaboratorSpecResponse(
    val name: String,
    val type: CollaboratorType,
    val agentTypeId: String? = null,
    val isLead: Boolean = false,
    // Phase 10+: instanceId and instanceStatus
)

@Serializable
data class EffortListResponse(
    val efforts: List<EffortSummaryResponse>,
)

@Serializable
data class EffortStatusResponse(
    val effortId: String,
    val status: String,
)
