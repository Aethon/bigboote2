package com.bigboote.coordinator.api.public.v1

import com.bigboote.coordinator.aggregates.agenttype.AgentTypeCommandHandler
import com.bigboote.coordinator.api.error.DomainException
import com.bigboote.coordinator.api.error.ValidationException
import com.bigboote.coordinator.api.public.v1.dto.*
import com.bigboote.coordinator.projections.AgentTypeSummaryProjection
import com.bigboote.coordinator.projections.repositories.AgentTypeReadRepository
import com.bigboote.coordinator.projections.repositories.AgentTypeRow
import com.bigboote.domain.commands.AgentTypeCommand.CreateAgentType
import com.bigboote.domain.commands.AgentTypeCommand.UpdateAgentType
import com.bigboote.domain.errors.DomainError
import com.bigboote.domain.values.AgentTypeId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.bigboote.coordinator.api.public.v1.AgentTypeRoutes")

/**
 * All 4 AgentType endpoints from the Coordinator Public API.
 *
 * Authentication is installed globally in Phase 7. For Phase 6, all endpoints are
 * unauthenticated.
 *
 * POST /api/v1/agent-types/create                 → CreateAgentType command
 * GET  /api/v1/agent-types                        → list all
 * GET  /api/v1/agent-types/{agentTypeId}          → get detail
 * POST /api/v1/agent-types/{agentTypeId}/update   → UpdateAgentType command
 *
 * The `id` field in the create request must be a fully-qualified AgentTypeId value
 * (e.g. "agenttype:lead-engineer"). The API doc illustrates this as "agt:..." but the
 * Event Schema doc (authoritative) requires the "agenttype:" prefix.
 *
 * See API Design doc Section 3.2.
 */
fun Route.agentTypeRoutes() {
    val commandHandler by application.inject<AgentTypeCommandHandler>()
    val projection by application.inject<AgentTypeSummaryProjection>()
    val readRepo by application.inject<AgentTypeReadRepository>()

    route("/agent-types") {

        // ------------------------------------------------------------------
        // POST /api/v1/agent-types/create
        // ------------------------------------------------------------------
        post("/create") {
            val req = call.receive<CreateAgentTypeRequest>()
            validateCreateRequest(req)

            val agentTypeId = parseAgentTypeId(req.id)

            val cmd = CreateAgentType(
                agentTypeId   = agentTypeId,
                name          = req.name.trim(),
                model         = req.model.trim(),
                systemPrompt  = req.systemPrompt.trim(),
                maxTokens     = req.modelParams.maxTokens,
                temperature   = req.modelParams.temperature,
                tools         = req.tools,
                dockerImage   = req.dockerImage.trim(),
                spawnStrategy = req.spawnStrategy.trim(),
            )

            commandHandler.handle(cmd)

            // Start catch-up subscription on the new agenttype stream.
            projection.trackAgentType(agentTypeId)

            logger.info("AgentType created via API: {}", agentTypeId)

            call.respond(
                HttpStatusCode.Created,
                CreateAgentTypeResponse(
                    agentTypeId = agentTypeId.value,
                    // DECISION: createdAt approximation — authoritative value is in the event.
                    createdAt = Clock.System.now().toString(),
                )
            )
        }

        // ------------------------------------------------------------------
        // GET /api/v1/agent-types
        // ------------------------------------------------------------------
        get {
            val agentTypes = readRepo.list()
            call.respond(HttpStatusCode.OK, AgentTypeListResponse(agentTypes.map { it.toDetailResponse() }))
        }

        // ------------------------------------------------------------------
        // GET /api/v1/agent-types/{agentTypeId}
        // ------------------------------------------------------------------
        get("/{agentTypeId}") {
            val agentTypeId = parseAgentTypeId(call.parameters["agentTypeId"])
            val agentType = readRepo.get(agentTypeId)
                ?: throw DomainException(DomainError.AgentTypeNotFound(agentTypeId))
            call.respond(HttpStatusCode.OK, agentType.toDetailResponse())
        }

        // ------------------------------------------------------------------
        // POST /api/v1/agent-types/{agentTypeId}/update
        // ------------------------------------------------------------------
        post("/{agentTypeId}/update") {
            val agentTypeId = parseAgentTypeId(call.parameters["agentTypeId"])
            val req = call.receive<UpdateAgentTypeRequest>()

            val cmd = UpdateAgentType(
                agentTypeId   = agentTypeId,
                name          = req.name?.trim(),
                model         = req.model?.trim(),
                systemPrompt  = req.systemPrompt?.trim(),
                maxTokens     = req.modelParams?.maxTokens,
                temperature   = req.modelParams?.temperature,
                tools         = req.tools,
                dockerImage   = req.dockerImage?.trim(),
                spawnStrategy = req.spawnStrategy?.trim(),
            )

            commandHandler.handle(cmd)

            logger.info("AgentType updated via API: {}", agentTypeId)

            call.respond(
                HttpStatusCode.OK,
                UpdateAgentTypeResponse(
                    agentTypeId = agentTypeId.value,
                    // DECISION: updatedAt approximation — authoritative value is in the event.
                    updatedAt = Clock.System.now().toString(),
                )
            )
        }
    }
}

// ------------------------------------------------------------------ private helpers

private fun validateCreateRequest(req: CreateAgentTypeRequest) {
    if (req.id.isBlank()) throw ValidationException("'id' must not be blank")
    if (req.name.isBlank()) throw ValidationException("'name' must not be blank")
    if (req.model.isBlank()) throw ValidationException("'model' must not be blank")
    if (req.systemPrompt.isBlank()) throw ValidationException("'systemPrompt' must not be blank")
    if (req.modelParams.maxTokens <= 0) {
        throw ValidationException("'modelParams.maxTokens' must be a positive integer")
    }
    req.modelParams.temperature?.let { t ->
        if (t < 0.0 || t > 1.0) {
            throw ValidationException("'modelParams.temperature' must be between 0.0 and 1.0")
        }
    }
    if (req.dockerImage.isBlank()) throw ValidationException("'dockerImage' must not be blank")
    if (req.spawnStrategy.isBlank()) throw ValidationException("'spawnStrategy' must not be blank")
}

private fun parseAgentTypeId(raw: String?): AgentTypeId {
    if (raw.isNullOrBlank()) throw ValidationException("Missing agentTypeId parameter")
    return try {
        AgentTypeId(raw)
    } catch (e: IllegalArgumentException) {
        throw ValidationException("Invalid agentTypeId '$raw': ${e.message}")
    }
}

private fun AgentTypeRow.toDetailResponse() = AgentTypeDetailResponse(
    agentTypeId   = agentTypeId.value,
    name          = name,
    model         = model,
    systemPrompt  = systemPrompt,
    maxTokens     = maxTokens,
    temperature   = temperature,
    tools         = tools,
    dockerImage   = dockerImage,
    spawnStrategy = spawnStrategy,
    createdAt     = createdAt.toString(),
    updatedAt     = updatedAt?.toString(),
)
