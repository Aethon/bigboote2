package com.bigboote.coordinator

import com.bigboote.coordinator.api.error.configureErrorHandling
import com.bigboote.coordinator.api.public.v1.agentTypeRoutes
import com.bigboote.coordinator.api.public.v1.conversationRoutes
import com.bigboote.coordinator.api.public.v1.documentRoutes
import com.bigboote.coordinator.api.public.v1.effortRoutes
import com.bigboote.coordinator.api.public.v1.messagingWebSocketRoutes
import com.bigboote.coordinator.api.public.v1.sseRoutes
import com.bigboote.coordinator.auth.configureAuth
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import com.bigboote.coordinator.koin.ApiModule
import com.bigboote.coordinator.koin.AuthModule
import com.bigboote.coordinator.koin.DomainModule
import com.bigboote.coordinator.koin.InfrastructureModule
import com.bigboote.coordinator.koin.MessagingModule
import com.bigboote.coordinator.koin.ProjectionModule
import com.bigboote.coordinator.koin.ProxyModule
import com.bigboote.coordinator.koin.ReactorModule
import com.bigboote.infra.koin.sharedInfraModule
import org.koin.core.module.Module
import org.koin.ktor.plugin.Koin
import kotlin.time.Duration.Companion.seconds

/**
 * All coordinator Koin modules in load order.
 *
 * Listed here so that both [Application.configureServer] (production) and
 * route-layer tests (via the overloaded koinModules parameter) share one
 * canonical module list.  Tests pass a smaller testModule() list instead.
 */
val COORDINATOR_KOIN_MODULES: List<Module> = listOf(
    sharedInfraModule,
    InfrastructureModule,
    AuthModule,
    DomainModule,
    ProjectionModule,
    ReactorModule,
    ProxyModule,
    MessagingModule,
    ApiModule,
)

/**
 * Configures the Ktor server with plugins, error handling, and routing.
 *
 * Installation order matters in Ktor — plugins are applied in order:
 * 1. ContentNegotiation  — JSON codec for request/response bodies
 * 2. Koin               — loads [koinModules] into an application-scoped context
 * 3. Authentication      — Bearer (public-api) and GatewayToken (agent-gateway)
 * 4. StatusPages         — maps exceptions to HTTP error responses
 * 5. WebSockets          — Phase 13: real-time messaging for external collaborators
 * 6. SSE                 — Phase 13: event stream for UI/agent clients
 * 7. Routing             — route handlers, grouped by auth scheme
 *
 * [koinModules] defaults to [COORDINATOR_KOIN_MODULES] for production.
 * Tests pass a smaller module list (test doubles only) to avoid needing
 * real infrastructure connections.
 *
 * In koin-ktor 4.x, install(Koin) creates an application-scoped isolated
 * context (not a bridge to the global startKoin context).  All beans
 * consumed via inject() in route handlers must be present in [koinModules].
 *
 * Auth scheme names:
 * - "public-api"    — Authorization: Bearer <token> for /api/v1/ routes
 * - "agent-gateway" — X-Gateway-Token for /internal/v1/ routes
 *
 * See Architecture doc Section 5.1 and API Design doc Section 2.
 */
fun Application.configureServer(koinModules: List<Module> = COORDINATOR_KOIN_MODULES) {
    install(ContentNegotiation) {
        json(Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            prettyPrint = false
        })
    }

    // Load Koin modules into an application-scoped isolated context (koin-ktor 4.x).
    // Route handlers resolve dependencies from this context via inject().
    install(Koin) { modules(koinModules) }

    // Install Authentication plugin with Bearer (public-api) and X-Gateway-Token (agent-gateway).
    // Must be installed before Routing so authenticate("...") blocks can reference providers.
    configureAuth()

    configureErrorHandling()

    // Phase 13: WebSockets — real-time messaging for external (human) collaborators.
    // pingPeriod and timeout keep connections alive through proxies and detect broken clients.
    install(WebSockets) {
        pingPeriod = 30.seconds
        timeout = 60.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    // Phase 13: Server-Sent Events — effort event stream for UI and agent clients.
    install(SSE)

    routing {
        // Health check — unauthenticated; used by runbook verification and load balancers
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        // Coordinator Public API — requires Authorization: Bearer <token>
        authenticate("public-api") {
            route("/api/v1") {
                effortRoutes()            // Phase 5
                agentTypeRoutes()         // Phase 6
                conversationRoutes()      // Phase 11
                sseRoutes()               // Phase 13: GET  /api/v1/efforts/{effortId}/stream
                messagingWebSocketRoutes() // Phase 13: WS  /api/v1/efforts/{effortId}/messaging
                documentRoutes()           // Phase 14: CRUD /api/v1/efforts/{effortId}/documents
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
