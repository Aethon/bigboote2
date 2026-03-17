package com.bigboote.coordinator

import com.bigboote.coordinator.api.error.configureErrorHandling
import com.bigboote.coordinator.api.public.v1.agentTypeRoutes
import com.bigboote.coordinator.api.public.v1.effortRoutes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.koin.ktor.plugin.Koin

/**
 * Configures the Ktor server with plugins, error handling, and routing.
 *
 * Phase 4 installs: ContentNegotiation (JSON), Koin bridge, StatusPages
 * (error handler), health check endpoint, and stub route groups for later phases.
 */
fun Application.configureServer() {
    install(ContentNegotiation) {
        json(Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            prettyPrint = false
        })
    }

    // Bridge Koin (already started in main) into Ktor so route handlers can use inject()
    install(Koin) { /* Koin context started in main(); this bridges it to Ktor */ }

    configureErrorHandling()

    // TODO: Phase 7 — install auth plugin (BearerTokenValidator, GatewayTokenValidator)
    // TODO: Phase 13 — install WebSockets
    // TODO: Phase 13 — install SSE

    routing {
        // Health check — used by runbook verification and load balancers
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        // Stub route groups — populated in later phases
        route("/api/v1") {
            effortRoutes()     // Phase 5
            agentTypeRoutes()  // Phase 6
            // Phase 11: Conversation routes
            // Phase 14: Document routes
            // Phase 13: SSE and WebSocket routes
        }

        route("/internal/v1") {
            // Phase 12: Agent Gateway API routes
        }
    }
}
