package com.bigboote.coordinator.auth

import com.bigboote.coordinator.configureServer
import com.bigboote.coordinator.projections.AgentTypeSummaryProjection
import com.bigboote.coordinator.projections.EffortSummaryProjection
import com.bigboote.coordinator.projections.repositories.AgentTypeReadRepository
import com.bigboote.coordinator.projections.repositories.EffortReadRepository
import com.bigboote.coordinator.aggregates.effort.EffortCommandHandler
import com.bigboote.coordinator.aggregates.agenttype.AgentTypeCommandHandler
import com.bigboote.domain.values.AgentId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Tests for the Phase 7 authentication layer installed by [configureAuth].
 *
 * Verifies the Phase 7 verification gate:
 *  - Public API routes (/api/v1/{*}) reject requests without Authorization header (401)
 *  - Public API routes reject requests with a malformed Authorization header (401)
 *  - Public API routes accept any well-formed Bearer token (200/any success code)
 *  - Agent Gateway routes (/internal/v1/{*}) reject requests without X-Gateway-Token (401)
 *  - Agent Gateway routes reject requests with an unknown X-Gateway-Token (401)
 *  - Agent Gateway routes accept a known X-Gateway-Token registered in TokenStore
 *  - Health check (/health) is unauthenticated (200)
 *
 * Uses a real [TokenStore] with a pre-registered token to test the Gateway path.
 */
class AuthPluginTest : DescribeSpec({

    val tokenStore = TokenStore()
    val knownGatewayToken = "gw-known-token-123"
    val knownAgentId = AgentId("agent:auth-test-agent")

    beforeEach {
        // Register a known gateway token for gateway auth tests
        tokenStore.register(
            agentId      = knownAgentId,
            gatewayToken = knownGatewayToken,
            agentToken   = "at-unused-in-phase-7",
        )

        try { stopKoin() } catch (_: Exception) { }
        startKoin {
            modules(module {
                // Domain / projection stubs — not exercised by auth tests
                single { mockk<EffortCommandHandler>(relaxed = true) }
                single { mockk<AgentTypeCommandHandler>(relaxed = true) }
                single { mockk<EffortSummaryProjection>(relaxed = true) }
                single { mockk<EffortReadRepository>(relaxed = true) }
                single { mockk<AgentTypeSummaryProjection>(relaxed = true) }
                single { mockk<AgentTypeReadRepository>(relaxed = true) }
                // Auth beans — use the real tokenStore so gateway token tests work
                single<BearerTokenValidator> { StubBearerTokenValidator() }
                single { tokenStore }
                single { GatewayTokenValidator(get()) }
            })
        }
    }

    afterEach {
        try { stopKoin() } catch (_: Exception) { }
    }

    // ------------------------------------------------------------------ /health

    describe("GET /health (unauthenticated)") {

        it("returns 200 without any auth header") {
            testApplication {
                application { configureServer() }
                val response = client.get("/health")
                response.status shouldBe HttpStatusCode.OK
            }
        }
    }

    // ------------------------------------------------------------------ public API bearer auth

    describe("Public API — Authorization: Bearer") {

        it("returns 401 when Authorization header is absent") {
            testApplication {
                application { configureServer() }
                // Use the default unauthenticated client (no Authorization header)
                val response = client.get("/api/v1/efforts")
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        it("returns 401 when Authorization header is present but not Bearer scheme") {
            testApplication {
                application { configureServer() }
                val response = client.get("/api/v1/efforts") {
                    headers { append(HttpHeaders.Authorization, "Basic dXNlcjpwYXNz") }
                }
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        it("returns 401 when Bearer token is empty string") {
            testApplication {
                application { configureServer() }
                val response = client.get("/api/v1/efforts") {
                    headers { append(HttpHeaders.Authorization, "Bearer ") }
                }
                // StubBearerTokenValidator.validate("") returns null → 401
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        it("returns non-401 when any non-blank Bearer token is provided") {
            testApplication {
                application { configureServer() }
                val response = client.get("/api/v1/efforts") {
                    headers { append(HttpHeaders.Authorization, "Bearer any-token-passes-stub") }
                }
                // StubBearerTokenValidator accepts any non-blank token — auth passes.
                // Relaxed mocks return defaults so the route handler succeeds.
                // The key assertion: NOT Unauthorized.
                response.status shouldNotBe HttpStatusCode.Unauthorized
            }
        }

        it("returns non-401 for a UUID-format Bearer token") {
            testApplication {
                application { configureServer() }
                val response = client.get("/api/v1/efforts") {
                    headers { append(HttpHeaders.Authorization, "Bearer a1b2c3d4-e5f6-7890-abcd-ef1234567890") }
                }
                // Auth passes — NOT 401 regardless of downstream status
                response.status shouldNotBe HttpStatusCode.Unauthorized
            }
        }
    }

    // ------------------------------------------------------------------ agent gateway token auth

    describe("Agent Gateway API — X-Gateway-Token") {

        it("returns 401 when X-Gateway-Token header is absent") {
            testApplication {
                application { configureServer() }
                val response = client.get("/internal/v1/anything")
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        it("returns 401 when X-Gateway-Token is an unknown value") {
            testApplication {
                application { configureServer() }
                val response = client.get("/internal/v1/anything") {
                    headers { append("X-Gateway-Token", "unknown-random-token") }
                }
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        it("passes auth (non-401) when X-Gateway-Token is a registered token") {
            testApplication {
                application { configureServer() }
                val response = client.get("/internal/v1/anything") {
                    headers { append("X-Gateway-Token", knownGatewayToken) }
                }
                // Auth passes — route doesn't exist yet (Phase 12), so Ktor returns 404.
                // Important: NOT 401.
                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})
