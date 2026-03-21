package com.bigboote.coordinator.api.error

import com.bigboote.domain.errors.DomainError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("com.bigboote.coordinator.api.error.ErrorHandler")

/**
 * Installs global exception handling that maps domain errors and infrastructure
 * exceptions to consistent ApiError responses. See API Design doc Section 6.
 */
fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<DomainException> { call, cause ->
            val (status, code) = when (cause.domainError) {
                is DomainError.EffortNotFound -> HttpStatusCode.NotFound to "EFFORT_NOT_FOUND"
                is DomainError.AgentTypeNotFound -> HttpStatusCode.NotFound to "AGENT_TYPE_NOT_FOUND"
                is DomainError.ConversationNotFound -> HttpStatusCode.NotFound to "CONVERSATION_NOT_FOUND"
                is DomainError.DocumentNotFound -> HttpStatusCode.NotFound to "DOCUMENT_NOT_FOUND"
                is DomainError.InvalidEffortTransition -> HttpStatusCode.UnprocessableEntity to "INVALID_STATE"
                is DomainError.EffortAlreadyClosed -> HttpStatusCode.UnprocessableEntity to "INVALID_STATE"
                is DomainError.InvalidAgentTypeSlug -> HttpStatusCode.BadRequest to "INVALID_REQUEST"
            }
            call.respond(status, ApiError(ErrorDetail(code = code, message = cause.domainError.message)))
        }

        exception<ValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(ErrorDetail(code = "INVALID_REQUEST", message = cause.message ?: "Validation failed")),
            )
        }

        exception<ConflictException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ApiError(ErrorDetail(code = "CONFLICT", message = cause.message ?: "State conflict")),
            )
        }

        exception<Throwable> { call, cause ->
            val correlationId = UUID.randomUUID().toString()
            logger.error("Unhandled exception [correlationId={}]", correlationId, cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(
                    ErrorDetail(
                        code = "INTERNAL_ERROR",
                        message = "An unexpected error occurred.",
                        detail = correlationId,
                    )
                ),
            )
        }
    }
}

/**
 * Wraps a [DomainError] for propagation through Ktor's exception handling.
 * Used at the boundary between domain code (which returns Either) and API routes.
 */
class DomainException(val domainError: DomainError) : RuntimeException(domainError.message)

/**
 * Thrown when request validation fails (400).
 */
class ValidationException(message: String) : RuntimeException(message)

/**
 * Thrown on optimistic concurrency or state conflicts (409).
 */
class ConflictException(message: String) : RuntimeException(message)
