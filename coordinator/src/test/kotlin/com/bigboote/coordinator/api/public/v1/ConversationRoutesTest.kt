package com.bigboote.coordinator.api.public.v1

import com.bigboote.coordinator.aggregates.conversation.ConversationCommandHandler
import com.bigboote.coordinator.api.error.DomainException
import com.bigboote.coordinator.auth.BearerTokenValidator
import com.bigboote.coordinator.auth.GatewayTokenValidator
import com.bigboote.coordinator.auth.StubBearerTokenValidator
import com.bigboote.coordinator.auth.TokenStore
import com.bigboote.coordinator.configureServer
import com.bigboote.coordinator.projections.ConversationProjection
import com.bigboote.coordinator.projections.repositories.ConversationReadRepository
import com.bigboote.coordinator.projections.repositories.ConversationRow
import com.bigboote.coordinator.projections.repositories.MessageRow
import com.bigboote.domain.errors.DomainError
import com.bigboote.domain.values.EffortId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.mockk
import kotlinx.datetime.Clock
import org.koin.dsl.module

/**
 * Integration tests for [conversationRoutes] that exercise the full Ktor request
 * pipeline with mocked dependencies.
 *
 * Infrastructure dependencies (ConversationCommandHandler, ConversationProjection,
 * ConversationReadRepository) are replaced with MockK stubs so that no KurrentDB or
 * Postgres connection is required.
 *
 * All requests to /api/v1/{*} require `Authorization: Bearer <token>`.
 * [authenticatedClient] provides a test client pre-configured with a stub bearer token.
 *
 * Channel names in URL paths are bare names without the '#' prefix (e.g. "general",
 * not "#general"). The response convId is the bare name (e.g. "general"), while convName
 * includes the '#' prefix (e.g. "#general").
 *
 * Test coverage:
 *  - POST /api/v1/efforts/{effortId}/conversations/create-channel — happy path + validation
 *  - GET  /api/v1/efforts/{effortId}/conversations               — empty + populated list
 *  - GET  /api/v1/efforts/{effortId}/conversations/{channelName}/messages — found + not found
 *  - POST /api/v1/efforts/{effortId}/conversations/{channelName}/members  — happy path + errors
 */
class ConversationRoutesTest : DescribeSpec({

    // ---- shared fixtures ----

    val effortId = EffortId("test-effort-001")
    // convId is the bare channel name (without '#' prefix)
    val channelName      = "general"
    val now = Clock.System.now()

    val sampleConversationRow = ConversationRow(
        effortId  = effortId,
        convId    = channelName,
        convName  = "#general",
        members   = listOf("@alice", "@lead-dev"),
        createdAt = now,
    )

    val sampleMessageRow = MessageRow(
        messageId = "msg:abc123",
        convId    = channelName,
        effortId  = effortId,
        fromName  = "@alice",
        body      = "Hello, world!",
        postedAt  = now,
    )

    // ---- mocks ----

    lateinit var commandHandler: ConversationCommandHandler
    lateinit var projection: ConversationProjection
    lateinit var readRepo: ConversationReadRepository

    beforeEach {
        commandHandler = mockk()
        projection     = mockk(relaxed = true)
        readRepo       = mockk()
    }

    // Build a per-test Koin module from the current mocks.
    // koin-ktor 4.x creates a fresh isolated application context per testApplication,
    // so no startKoin/stopKoin lifecycle management is needed.
    fun testModule() = module {
        single { commandHandler }
        single { projection }
        single { readRepo }
        // Effort / AgentType stubs — not exercised by conversation tests but
        // registered defensively in case a route handler resolves them eagerly.
        single { mockk<com.bigboote.coordinator.aggregates.effort.EffortCommandHandler>(relaxed = true) }
        single { mockk<com.bigboote.coordinator.aggregates.agenttype.AgentTypeCommandHandler>(relaxed = true) }
        single { mockk<com.bigboote.coordinator.projections.EffortSummaryProjection>(relaxed = true) }
        single { mockk<com.bigboote.coordinator.projections.AgentTypeSummaryProjection>(relaxed = true) }
        single { mockk<com.bigboote.coordinator.projections.repositories.EffortReadRepository>(relaxed = true) }
        single { mockk<com.bigboote.coordinator.projections.repositories.AgentTypeReadRepository>(relaxed = true) }
        // Auth beans — required because configureServer() installs auth
        single<BearerTokenValidator> { StubBearerTokenValidator() }
        single { TokenStore() }
        single { GatewayTokenValidator(get()) }
    }

    // ---- POST /api/v1/efforts/{effortId}/conversations/create-channel ----

    describe("POST /api/v1/efforts/{effortId}/conversations/create-channel") {

        val validCreateBody = """
            {
              "name": "general",
              "members": ["@alice", "@lead-dev"]
            }
        """.trimIndent()

        it("returns 201 Created with convId, convName, and members for a valid request") {
            coJustRun { commandHandler.handle(any<com.bigboote.domain.commands.ConversationCommand.CreateChannel>()) }

            testApplication {
                application { configureServer(listOf(testModule())) }
                val client = authenticatedClient()
                val response = client.post("/api/v1/efforts/${effortId.value}/conversations/create-channel") {
                    contentType(ContentType.Application.Json)
                    setBody(validCreateBody)
                }

                response.status shouldBe HttpStatusCode.Created
                val body = response.bodyAsText()
                // convId is bare name (no '#' prefix), convName has '#' prefix
                body shouldContain "\"convId\":\"general\""
                body shouldContain "\"convName\":\"#general\""
                body shouldContain "\"@alice\""
                body shouldContain "\"@lead-dev\""
                body shouldContain "\"createdAt\""
            }
        }

        it("returns 400 when name is blank") {
            testApplication {
                application { configureServer(listOf(testModule())) }
                val client = authenticatedClient()
                val response = client.post("/api/v1/efforts/${effortId.value}/conversations/create-channel") {
                    contentType(ContentType.Application.Json)
                    setBody("""{ "name": "  ", "members": ["@alice"] }""")
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "name"
            }
        }

        it("returns 400 when members list is empty") {
            testApplication {
                application { configureServer(listOf(testModule())) }
                val client = authenticatedClient()
                val response = client.post("/api/v1/efforts/${effortId.value}/conversations/create-channel") {
                    contentType(ContentType.Application.Json)
                    setBody("""{ "name": "general", "members": [] }""")
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "members"
            }
        }

        it("returns 400 when a member name lacks a prefix") {
            testApplication {
                application { configureServer(listOf(testModule())) }
                val client = authenticatedClient()
                val response = client.post("/api/v1/efforts/${effortId.value}/conversations/create-channel") {
                    contentType(ContentType.Application.Json)
                    setBody("""{ "name": "general", "members": ["alice"] }""")
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "member"
            }
        }
    }

    // ---- GET /api/v1/efforts/{effortId}/conversations ----

    describe("GET /api/v1/efforts/{effortId}/conversations") {

        it("returns 200 with empty conversations list when none exist") {
            coEvery { readRepo.list(effortId) } returns emptyList()

            testApplication {
                application { configureServer(listOf(testModule())) }
                val client = authenticatedClient()
                val response = client.get("/api/v1/efforts/${effortId.value}/conversations")

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldContain "\"conversations\":[]"
            }
        }

        it("returns 200 with populated list") {
            coEvery { readRepo.list(effortId) } returns listOf(sampleConversationRow)

            testApplication {
                application { configureServer(listOf(testModule())) }
                val client = authenticatedClient()
                val response = client.get("/api/v1/efforts/${effortId.value}/conversations")

                response.status shouldBe HttpStatusCode.OK
                val body = response.bodyAsText()
                body shouldContain "\"convId\":\"general\""
                body shouldContain "\"convName\":\"#general\""
                body shouldContain "\"@alice\""
            }
        }
    }

    // ---- GET /api/v1/efforts/{effortId}/conversations/{channelName}/messages ----

    describe("GET /api/v1/efforts/{effortId}/conversations/{channelName}/messages") {

        it("returns 200 with messages when conversation exists") {
            coEvery { readRepo.get(effortId, channelName) } returns sampleConversationRow
            coEvery { readRepo.getMessages(effortId, channelName, 0, 50) } returns listOf(sampleMessageRow)

            testApplication {
                application { configureServer(listOf(testModule())) }
                val client = authenticatedClient()
                val response = client.get(
                    "/api/v1/efforts/${effortId.value}/conversations/$channelName/messages"
                )

                response.status shouldBe HttpStatusCode.OK
                val body = response.bodyAsText()
                body shouldContain "\"convId\":\"general\""
                body shouldContain "\"messageId\":\"msg:abc123\""
                body shouldContain "\"from\":\"@alice\""
                body shouldContain "\"body\":\"Hello, world!\""
                body shouldContain "\"from\":0"
                body shouldContain "\"limit\":50"
            }
        }

        it("returns 200 with empty messages list when conversation exists but has no messages") {
            coEvery { readRepo.get(effortId, channelName) } returns sampleConversationRow
            coEvery { readRepo.getMessages(effortId, channelName, 0, 50) } returns emptyList()

            testApplication {
                application { configureServer(listOf(testModule())) }
                val client = authenticatedClient()
                val response = client.get(
                    "/api/v1/efforts/${effortId.value}/conversations/$channelName/messages"
                )

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldContain "\"messages\":[]"
            }
        }

        it("returns 404 when the conversation does not exist") {
            coEvery { readRepo.get(effortId, channelName) } returns null

            testApplication {
                application { configureServer(listOf(testModule())) }
                val client = authenticatedClient()
                val response = client.get(
                    "/api/v1/efforts/${effortId.value}/conversations/$channelName/messages"
                )

                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        it("respects from and limit query parameters") {
            coEvery { readRepo.get(effortId, channelName) } returns sampleConversationRow
            coEvery { readRepo.getMessages(effortId, channelName, 10, 20) } returns emptyList()

            testApplication {
                application { configureServer(listOf(testModule())) }
                val client = authenticatedClient()
                val response = client.get(
                    "/api/v1/efforts/${effortId.value}/conversations/$channelName/messages?from=10&limit=20"
                )

                response.status shouldBe HttpStatusCode.OK
                val body = response.bodyAsText()
                body shouldContain "\"from\":10"
                body shouldContain "\"limit\":20"
            }
        }
    }

    // ---- POST /api/v1/efforts/{effortId}/conversations/{channelName}/members ----

    describe("POST /api/v1/efforts/{effortId}/conversations/{channelName}/members") {

        it("returns 200 with convId and member on success") {
            coJustRun { commandHandler.handle(any<com.bigboote.domain.commands.ConversationCommand.AddMembers>()) }

            testApplication {
                application { configureServer(listOf(testModule())) }
                val client = authenticatedClient()
                val response = client.post(
                    "/api/v1/efforts/${effortId.value}/conversations/$channelName/members"
                ) {
                    contentType(ContentType.Application.Json)
                    setBody("""{ "member": "@bob" }""")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.bodyAsText()
                body shouldContain "\"convId\":\"general\""
                body shouldContain "\"member\":\"@bob\""
            }
        }

        it("returns 400 when member is blank") {
            testApplication {
                application { configureServer(listOf(testModule())) }
                val client = authenticatedClient()
                val response = client.post(
                    "/api/v1/efforts/${effortId.value}/conversations/$channelName/members"
                ) {
                    contentType(ContentType.Application.Json)
                    setBody("""{ "member": "  " }""")
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "member"
            }
        }

        it("returns 400 when member name lacks a prefix") {
            testApplication {
                application { configureServer(listOf(testModule())) }
                val client = authenticatedClient()
                val response = client.post(
                    "/api/v1/efforts/${effortId.value}/conversations/$channelName/members"
                ) {
                    contentType(ContentType.Application.Json)
                    setBody("""{ "member": "bob" }""")
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "member"
            }
        }

        it("returns 404 when the conversation does not exist") {
            coEvery {
                commandHandler.handle(any<com.bigboote.domain.commands.ConversationCommand.AddMembers>())
            } throws DomainException(DomainError.ConversationNotFound(channelName))

            testApplication {
                application { configureServer(listOf(testModule())) }
                val client = authenticatedClient()
                val response = client.post(
                    "/api/v1/efforts/${effortId.value}/conversations/$channelName/members"
                ) {
                    contentType(ContentType.Application.Json)
                    setBody("""{ "member": "@bob" }""")
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})

// ---- test helpers ----

/**
 * Creates a test HTTP client pre-configured with the `Authorization: Bearer test-token`
 * header so that all requests pass the Phase 7 public-api auth check.
 */
private fun ApplicationTestBuilder.authenticatedClient() = createClient {
    defaultRequest {
        headers.append(HttpHeaders.Authorization, "Bearer test-token")
    }
}
