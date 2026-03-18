package com.bigboote.coordinator

import com.bigboote.coordinator.api.error.configureErrorHandling
import com.bigboote.coordinator.api.public.v1.agentTypeRoutes
import com.bigboote.coordinator.api.public.v1.conversationRoutes
import com.bigboote.coordinator.api.public.v1.effortRoutes
import com.bigboote.coordinator.auth.configureAuth
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.koin.ktor.plugin.Koin

/**
 * Configures the Ktor server with plugins, error handling, and routing.
 *
 * Installation order matters in Ktor — plugins are applied in order:
 * 1. ContentNegotiation  — JSON codec for request/response bodies
 * 2. Koin bridge         — exposes the global Koin context to route handlers
 * 3. Authentication      — Bearer (public-api) and GatewayToken (agent-gateway)
 * 4. StatusPages         — maps exceptions to HTTP error responses
 * 5. Routing             — route handlers, grouped by auth scheme
 *
 * Auth scheme names:
 * - "public-api"    — Authorization: Bearer <token> for /api/v1/ routes
 * - "agent-gateway" — X-Gateway-Token for /internal/v1/ routes
 *
 * See Architecture doc Section 5.1 and API Design doc Section 2.
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

    // Install Authentication plugin with Bearer (public-api) and X-Gateway-Token (agent-gateway).
    // Must be installed before Routing so authenticate("...") blocks can reference providers.
    configureAuth()

    configureErrorHandling()

    // TODO: Phase 13 — install WebSockets
    // TODO: Phase 13 — install SSE

    routing {
        // Health check — unauthenticated; used by runbook verification and load balancers
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        // Coordinator Public API — requires Authorization: Bearer <token>
        authenticate("public-api") {
            route("/api/v1") {
                effortRoutes()     // Phase 5
                agentTypeRoutes()  // Phase 6
                conversationRoutes()  // Phase 11
                // Phase 14: Document routes
                // Phase 13: SSE and WebSocket routes
            }
        }

        // Agent Gateway API — requires X-Gateway-Token: <per-instance-token>
        authenticate("agent-gateway") {
            route("/internal/v1") {
                // Phase 12: Agent Gateway API routes
            }
        }
    }
}
