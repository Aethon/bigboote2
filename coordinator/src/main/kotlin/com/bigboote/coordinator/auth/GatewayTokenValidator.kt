package com.bigboote.coordinator.auth

import com.bigboote.domain.values.AgentId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.response.respond

/**
 * Represents an authenticated agent on the Agent Gateway API.
 *
 * Resolved by [GatewayTokenValidator] from the `X-Gateway-Token` header.
 * Routes in `AgentGatewayRoutes` (Phase 12) call `call.principal<AgentPrincipal>()`
 * to obtain the caller's identity for per-stream access enforcement.
 *
 * Note: Ktor 2.3+ no longer requires principal types to implement Principal.
 */
data class AgentPrincipal(val agentId: AgentId)

/**
 * Validates the `X-Gateway-Token` header value for requests to the Agent
 * Gateway API (`/internal/v1/`).
 *
 * Delegates to [TokenStore.resolveGatewayToken]: returns the [AgentId] that
 * owns the token, or null if the token is unknown / the store is empty (which
 * will cause Ktor to respond with 401).
 *
 * See API Design doc Section 2.2 and Architecture doc Section 10.
 */
class GatewayTokenValidator(private val tokenStore: TokenStore) {
    fun validate(token: String): AgentId? = tokenStore.resolveGatewayToken(token)
}

// ------------------------------------------------------------------ Ktor provider

/**
 * Custom Ktor [AuthenticationProvider] that reads the `X-Gateway-Token` request
 * header and resolves it to an [AgentPrincipal] via [GatewayTokenValidator].
 *
 * Missing header → 401 with NoCredentials cause.
 * Unknown token  → 401 with InvalidCredentials cause.
 * Valid token    → principal set to [AgentPrincipal] and request proceeds.
 */
private class GatewayTokenAuthProvider(
    config: Config,
    private val validator: GatewayTokenValidator,
) : AuthenticationProvider(config) {

    class Config(name: String) : AuthenticationProvider.Config(name)

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val token = context.call.request.headers["X-Gateway-Token"]

        if (token.isNullOrBlank()) {
            context.challenge(
                "GatewayToken",
                AuthenticationFailedCause.NoCredentials,
            ) { challenge, call ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "UNAUTHORIZED", "message" to "Missing X-Gateway-Token header"),
                )
                challenge.complete()
            }
            return
        }

        val agentId = validator.validate(token)
        if (agentId == null) {
            context.challenge(
                "GatewayToken",
                AuthenticationFailedCause.InvalidCredentials,
            ) { challenge, call ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "UNAUTHORIZED", "message" to "Invalid X-Gateway-Token"),
                )
                challenge.complete()
            }
        } else {
            context.principal(AgentPrincipal(agentId))
        }
    }
}

/**
 * Registers a [GatewayTokenAuthProvider] with the given [name] in the Ktor
 * [AuthenticationConfig]. Called from [configureAuth] in AuthPlugin.kt.
 *
 * Usage:
 * ```kotlin
 * install(Authentication) {
 *     gatewayToken("agent-gateway", gatewayValidator)
 * }
 * ```
 */
fun AuthenticationConfig.gatewayToken(name: String, validator: GatewayTokenValidator) {
    register(GatewayTokenAuthProvider(GatewayTokenAuthProvider.Config(name), validator))
}
