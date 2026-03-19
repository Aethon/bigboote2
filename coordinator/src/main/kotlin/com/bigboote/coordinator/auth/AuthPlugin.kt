package com.bigboote.coordinator.auth

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import org.koin.ktor.ext.inject

/**
 * Installs Ktor [Authentication] and wires both auth schemes used by the
 * Coordinator:
 *
 * - `"public-api"` — Bearer token scheme for `/api/v1/` routes.
 *   Uses [BearerTokenValidator]; in Phase 7, the stub accepts any non-blank token.
 *
 * - `"agent-gateway"` — Custom header scheme for `/internal/v1/` routes.
 *   Reads `X-Gateway-Token` and validates via [GatewayTokenValidator] /
 *   [TokenStore]. Resolves to [AgentPrincipal] on success.
 *
 * [Authentication] must be installed before [io.ktor.server.routing.Routing] so
 * that `authenticate("public-api") { }` and `authenticate("agent-gateway") { }`
 * blocks in routing can reference the configured providers by name.
 *
 * Validators are resolved lazily from the Koin context so that this function
 * can be called during server setup, before the first request arrives.
 *
 * See API Design doc Section 2 and Architecture doc Section 5.1.
 */
fun Application.configureAuth() {
    val bearerValidator by inject<BearerTokenValidator>()
    val gatewayValidator by inject<GatewayTokenValidator>()

    install(Authentication) {

        // ----------------------------------------------------------------
        // Public API — Authorization: Bearer <token>
        // ----------------------------------------------------------------
        // Ktor's built-in bearer provider reads the Authorization header,
        // strips the "Bearer " prefix, and passes the token to the
        // authenticate block. Returning null causes a 401 response.
        bearer("public-api") {
            authenticate { credential ->
                bearerValidator.validate(credential.token)
            }
        }

        // ----------------------------------------------------------------
        // Agent Gateway API — X-Gateway-Token: <per-instance-token>
        // ----------------------------------------------------------------
        // Custom header provider registered via our gatewayToken extension.
        // Returns AgentPrincipal on success; 401 if token is missing or unknown.
        gatewayToken("agent-gateway", gatewayValidator)
    }
}
