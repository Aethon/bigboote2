package com.bigboote.coordinator.api.public.v1

import com.bigboote.coordinator.aggregates.effort.EffortCommandHandler
import com.bigboote.coordinator.api.error.DomainException
import com.bigboote.coordinator.api.error.ValidationException
import com.bigboote.coordinator.api.public.v1.dto.*
import com.bigboote.coordinator.projections.EffortSummaryProjection
import com.bigboote.coordinator.projections.repositories.EffortReadRepository
import com.bigboote.coordinator.projections.repositories.EffortRow
import com.bigboote.domain.aggregates.EffortStatus
import com.bigboote.domain.commands.EffortCommand.*
import com.bigboote.domain.errors.DomainError
import com.bigboote.domain.values.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.SerializationException
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.bigboote.coordinator.api.public.v1.EffortRoutes")

/**
 * All 7 Effort endpoints from the Coordinator Public API.
 *
 * Authentication is installed globally in Phase 7. For Phase 5, all endpoints are
 * unauthenticated.
 *
 * POST /api/v1/efforts/create             → CreateEffort command
 * GET  /api/v1/efforts                    → list (optional ?status= filter)
 * GET  /api/v1/efforts/{effortId}         → get detail
 * POST /api/v1/efforts/{effortId}/start   → StartEffort command
 * POST /api/v1/efforts/{effortId}/pause   → PauseEffort command
 * POST /api/v1/efforts/{effortId}/resume  → ResumeEffort command
 * POST /api/v1/efforts/{effortId}/close   → CloseEffort command
 *
 * Read-after-write consistency model: after each command, [EffortSummaryProjection]
 * is notified to start/refresh its catch-up subscription for the effort stream.
 * The subscription processes events nearly instantaneously in the same process.
 * For the manual curl verification gate, the delay is negligible.
 *
 * See API Design doc Section 3.1.
 */
fun Route.effortRoutes() {
    val commandHandler by application.inject<EffortCommandHandler>()
    val projection by application.inject<EffortSummaryProjection>()
    val readRepo by application.inject<EffortReadRepository>()

    route("/efforts") {

        // ------------------------------------------------------------------
        // POST /api/v1/efforts/create
        // ------------------------------------------------------------------
        post("/create") {
            val req = try {
                call.receive<CreateEffortRequest>()
            } catch (e: Exception) {
                throw ValidationException(e.message ?: "Invalid JSON")
            }
            validateCreateRequest(req)

            val collaborators = req.collaborators.map { it.toDomain() }
            val leadSpec = req.collaborators.first { it.isLead }
            val leadName = CollaboratorName.Individual(leadSpec.name)

            val effortId = EffortId.generate()
            val cmd = CreateEffort(
                effortId = effortId,
                name = req.name.trim(),
                goal = req.goal.trim(),
                collaborators = collaborators,
                leadName = leadName,
            )

            commandHandler.handle(cmd)

            // Start catch-up subscription on the new effort stream.
            // The subscription processes EffortCreated (and subsequent events) and writes
            // to EffortTable. Because KurrentDB acknowledges the write before this call,
            // the subscription starts after the event is already durable.
            projection.trackEffort(effortId)

            logger.info("Effort created via API: {}", effortId)

            call.respond(
                HttpStatusCode.Created,
                CreateEffortResponse(
                    effortId = effortId.value,
                    status = EffortStatus.CREATED.name.lowercase(),
                    // DECISION: createdAt in the response uses the current wall clock as a
                    // close approximation. The authoritative timestamp is in the EffortCreated
                    // event stored in KurrentDB; the read model reflects it after projection runs.
                    createdAt = Clock.System.now().toString(),
                )
            )
        }

        // ------------------------------------------------------------------
        // GET /api/v1/efforts?status=created|active|paused|closed
        // ------------------------------------------------------------------
        get {
            val statusParam = call.request.queryParameters["status"]
            val filter = statusParam?.let { parseStatus(it) }
            val efforts = readRepo.list(filter)
            call.respond(HttpStatusCode.OK, EffortListResponse(efforts.map { it.toSummaryResponse() }))
        }

        // ------------------------------------------------------------------
        // GET /api/v1/efforts/{effortId}
        // ------------------------------------------------------------------
        get("/{effortId}") {
            val effortId = parseEffortId(call.parameters["effortId"])
            val effort = readRepo.get(effortId)
                ?: throw DomainException(DomainError.EffortNotFound(effortId))
            call.respond(HttpStatusCode.OK, effort.toSummaryResponse())
        }

        // ------------------------------------------------------------------
        // POST /api/v1/efforts/{effortId}/start
        // ------------------------------------------------------------------
        post("/{effortId}/start") {
            val effortId = parseEffortId(call.parameters["effortId"])
            commandHandler.handle(StartEffort(effortId))
            // Subscription for this effort was started on create; no trackEffort needed here.
            logger.info("Effort started via API: {}", effortId)
            call.respond(HttpStatusCode.OK, EffortStatusResponse(effortId.value, EffortStatus.ACTIVE.name.lowercase()))
        }

        // ------------------------------------------------------------------
        // POST /api/v1/efforts/{effortId}/pause
        // ------------------------------------------------------------------
        post("/{effortId}/pause") {
            val effortId = parseEffortId(call.parameters["effortId"])
            commandHandler.handle(PauseEffort(effortId))
            logger.info("Effort paused via API: {}", effortId)
            call.respond(HttpStatusCode.OK, EffortStatusResponse(effortId.value, EffortStatus.PAUSED.name.lowercase()))
        }

        // ------------------------------------------------------------------
        // POST /api/v1/efforts/{effortId}/resume
        // ------------------------------------------------------------------
        post("/{effortId}/resume") {
            val effortId = parseEffortId(call.parameters["effortId"])
            commandHandler.handle(ResumeEffort(effortId))
            logger.info("Effort resumed via API: {}", effortId)
            call.respond(HttpStatusCode.OK, EffortStatusResponse(effortId.value, EffortStatus.ACTIVE.name.lowercase()))
        }

        // ------------------------------------------------------------------
        // POST /api/v1/efforts/{effortId}/close
        // ------------------------------------------------------------------
        post("/{effortId}/close") {
            val effortId = parseEffortId(call.parameters["effortId"])
            commandHandler.handle(CloseEffort(effortId))
            logger.info("Effort closed via API: {}", effortId)
            call.respond(HttpStatusCode.OK, EffortStatusResponse(effortId.value, EffortStatus.CLOSED.name.lowercase()))
        }
    }
}

// ------------------------------------------------------------------ private helpers

private fun validateCreateRequest(req: CreateEffortRequest) {
    if (req.name.isBlank()) throw ValidationException("'name' must not be blank")
    if (req.goal.isBlank()) throw ValidationException("'goal' must not be blank")
    if (req.collaborators.isEmpty()) throw ValidationException("'collaborators' must not be empty")
    val leadCount = req.collaborators.count { it.isLead }
    if (leadCount != 1) throw ValidationException("Exactly one collaborator must have isLead=true, found $leadCount")
    req.collaborators.forEach { spec ->
        if (spec.name.isBlank()) throw ValidationException("Collaborator 'name' must not be blank")
        if (spec.type == CollaboratorType.AGENT && spec.agentTypeId == null) {
            throw ValidationException("AGENT collaborator '${spec.name}' must specify 'agentTypeId'")
        }
    }
}

private fun CollaboratorSpecRequest.toDomain(): CollaboratorSpec {
    val domainName = CollaboratorName.Individual(this.name)
    val domainAgentTypeId = agentTypeId?.let {
        try {
            AgentTypeId(it)
        } catch (e: IllegalArgumentException) {
            throw ValidationException("Invalid agentTypeId '$it': ${e.message}")
        }
    }
    return CollaboratorSpec(
        name = domainName,
        type = this.type,
        agentTypeId = domainAgentTypeId,
        isLead = this.isLead,
    )
}

private fun parseEffortId(raw: String?): EffortId {
    if (raw.isNullOrBlank()) throw ValidationException("Missing effortId path parameter")
    return try {
        EffortId(raw)
    } catch (e: IllegalArgumentException) {
        throw ValidationException("Invalid effortId: '$raw'")
    }
}

private fun parseStatus(raw: String): EffortStatus =
    EffortStatus.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        ?: throw ValidationException(
            "Invalid status filter '$raw'. Allowed: ${EffortStatus.entries.joinToString { it.name.lowercase() }}"
        )

private fun EffortRow.toSummaryResponse(): EffortSummaryResponse {
    // Strip @ or # prefix from leadName for the API response
    val leadBare = leadName.removePrefix("@").removePrefix("#")

    val collaboratorResponses = collaborators.map { spec ->
        CollaboratorSpecResponse(
            name = spec.name.simple,   // CollaboratorName.simple is the bare name without prefix
            type = spec.type,
            agentTypeId = spec.agentTypeId?.value,
            isLead = spec.isLead,
        )
    }

    return EffortSummaryResponse(
        effortId = effortId.value,
        name = name,
        goal = goal,
        status = status.name.lowercase(),
        lead = leadBare,
        collaborators = collaboratorResponses,
        createdAt = createdAt.toString(),
        startedAt = startedAt?.toString(),
        closedAt = closedAt?.toString(),
    )
}
