package com.bigboote.coordinator.auth

/**
 * Represents an authenticated Coordinator Public API user.
 *
 * In production this is populated from a validated JWT (sub claim → userId,
 * a Bigboote-specific roles claim). In the Phase 7 stub, all well-formed
 * Bearer tokens yield the dev principal below.
 *
 * Used as the principal type for the "public-api" bearer auth scheme —
 * routes call `call.principal<UserPrincipal>()` after authentication succeeds.
 * Note: Ktor 2.3+ no longer requires principal types to implement Principal.
 */
data class UserPrincipal(
    val userId: String,
    val collaboratorName: String,
    val roles: List<String>,
)

/**
 * Validates the token string extracted from an `Authorization: Bearer <token>`
 * header for the Coordinator Public API.
 *
 * Returns a [UserPrincipal] on success, or null to signal authentication failure
 * (Ktor will respond with 401).
 */
interface BearerTokenValidator {
    fun validate(token: String): UserPrincipal?
}

/**
 * Phase 7 stub: accepts any non-blank Bearer token and returns a fixed dev
 * principal. JWT signature verification and expiry checks are deliberately
 * skipped until the production validator is wired in a later phase.
 *
 * Controlled by `BIGBOOTE_AUTH_STUB=true` in the environment (see API Design
 * doc Section 2.1). In Phase 7, stubbing is always active.
 */
class StubBearerTokenValidator : BearerTokenValidator {
    override fun validate(token: String): UserPrincipal? {
        if (token.isBlank()) return null
        return UserPrincipal(
            userId           = "user:dev",
            collaboratorName = "@dev",
            roles            = listOf("admin"),
        )
    }
}
