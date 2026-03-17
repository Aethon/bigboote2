package com.bigboote.coordinator.api.public.v1

import com.bigboote.coordinator.aggregates.agenttype.AgentTypeCommandHandler
import com.bigboote.coordinator.api.error.DomainException
import com.bigboote.coordinator.auth.BearerTokenValidator
import com.bigboote.coordinator.auth.GatewayTokenValidator
import com.bigboote.coordinator.auth.StubBearerTokenValidator
import com.bigboote.coordinator.auth.TokenStore
import com.bigboote.coordinator.configureServer
import com.bigboote.coordinator.projections.AgentTypeSummaryProjection
import com.bigboote.coordinator.projections.repositories.AgentTypeReadRepository
import com.bigboote.coordinator.projections.repositories.AgentTypeRow
import com.bigboote.domain.errors.DomainError
import com.bigboote.domain.values.AgentTypeId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.plugins.defaultrequest.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.mockk
import kotlinx.datetime.Clock
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Integration tests for [agentTypeRoutes] that exercise the full Ktor request
 * pipeline with mocked dependencies.
 *
 * Infrastructure dependencies (AgentTypeCommandHandler, AgentTypeSummaryProjection,
 * AgentTypeReadRepository) are replaced with MockK stubs so that no KurrentDB or
 * Postgres connection is required.
 *
 * Phase 7 addition: all requests to /api/v1/* require
 * `Authorization: Bearer <token>`. [authenticatedClient] provides a default
 * test client pre-configured with a stub bearer token. Auth beans
 * (BearerTokenValidator, TokenStore, GatewayTokenValidator) are registered in
 * the Koin test module.
 *
 * Test coverage:
 *  - POST /api/v1/agent-types/create — happy path + validation failures
 *  - GET  /api/v1/agent-types        — list all (empty + non-empty)
 *  - GET  /api/v1/agent-types/{id}   — found + not found + invalid ID
 *  - POST /api/v1/agent-types/{id}/update — happy path + not found
 */
class AgentTypeRoutesTest : DescribeSpec({

    // ---- shared fixtures ----

    val agentTypeId = AgentTypeId("agenttype:lead-engineer")
    val now = Clock.System.now()

    val sampleRow = AgentTypeRow(
        agentTypeId   = agentTypeId,
        name          = "Lead Engineer",
        model         = "claude-opus-4-6",
        systemPrompt  = "You are a lead software engineer.",
        maxTokens     = 8192,
        temperature   = 0.7,
        tools         = listOf("bash", "read_file"),
        dockerImage   = "bigboote/agent:latest",
        spawnStrategy = "DOCKER",
        createdAt     = now,
        updatedAt     = null,
    )

    val validCreateBody = """
        {
          "id": "agenttype:lead-engineer",
          "name": "Lead Engineer",
          "model": "claude-opus-4-6",
          "systemPrompt": "You are a lead software engineer.",
          "modelParams": { "maxTokens": 8192, "temperature": 0.7 },
          "tools": ["bash", "read_file"],
          "dockerImage": "bigboote/agent:latest",
          "spawnStrategy": "DOCKER"
        }
    """.trimIndent()

    // ---- mocks + Koin lifecycle ----

    lateinit var commandHandler: AgentTypeCommandHandler
    lateinit var projection: AgentTypeSummaryProjection
    lateinit var readRepo: AgentTypeReadRepository

    beforeEach {
        commandHandler = mockk()
        projection = mockk(relaxed = true)  // trackAgentType is a side-effect we don't assert here
        readRepo = mockk()

        try { stopKoin() } catch (_: Exception) { }
        startKoin {
            modules(module {
                single { commandHandler }
                single { projection }
                single { readRepo }
                // Phase 7: auth stubs — required because configureServer() installs auth
                single<BearerTokenValidator> { StubBearerTokenValidator() }
                single { TokenStore() }
                single { GatewayTokenValidator(get()) }
            })
        }
    }

    afterEach {
        try { stopKoin() } catch (_: Exception) { }
    }

    // ---- POST /api/v1/agent-types/create ----

    describe("POST /api/v1/agent-types/create") {

        it("returns 201 Created with agentTypeId and createdAt for a valid request") {
            coEvery { commandHandler.handle(any<com.bigboote.domain.commands.AgentTypeCommand.CreateAgentType>()) } returns agentTypeId

            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.post("/api/v1/agent-types/create") {
                    contentType(ContentType.Application.Json)
                    setBody(validCreateBody)
                }

                response.status shouldBe HttpStatusCode.Created
                val body = response.bodyAsText()
                body shouldContain "\"agentTypeId\":\"agenttype:lead-engineer\""
                body shouldContain "\"createdAt\""
            }
        }

        it("returns 201 when tools field is omitted (defaults to null / empty)") {
            coEvery { commandHandler.handle(any<com.bigboote.domain.commands.AgentTypeCommand.CreateAgentType>()) } returns agentTypeId

            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.post("/api/v1/agent-types/create") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "id": "agenttype:simple-agent",
                          "name": "Simple Agent",
                          "model": "claude-haiku-4-5-20251001",
                          "systemPrompt": "You are a simple agent.",
                          "modelParams": { "maxTokens": 1024 },
                          "dockerImage": "bigboote/simple:latest",
                          "spawnStrategy": "DOCKER"
                        }
                        """.trimIndent()
                    )
                }

                response.status shouldBe HttpStatusCode.Created
            }
        }

        it("returns 400 when id is blank") {
            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.post("/api/v1/agent-types/create") {
                    contentType(ContentType.Application.Json)
                    setBody(validCreateBody.replace("\"agenttype:lead-engineer\"", "\"\""))
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "id"
            }
        }

        it("returns 400 when name is blank") {
            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.post("/api/v1/agent-types/create") {
                    contentType(ContentType.Application.Json)
                    setBody(validCreateBody.replace("\"Lead Engineer\"", "\"   \""))
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "name"
            }
        }

        it("returns 400 when model is blank") {
            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.post("/api/v1/agent-types/create") {
                    contentType(ContentType.Application.Json)
                    setBody(validCreateBody.replace("\"claude-opus-4-6\"", "\"\""))
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "model"
            }
        }

        it("returns 400 when systemPrompt is blank") {
            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.post("/api/v1/agent-types/create") {
                    contentType(ContentType.Application.Json)
                    setBody(validCreateBody.replace("\"You are a lead software engineer.\"", "\"\""))
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "systemPrompt"
            }
        }

        it("returns 400 when maxTokens is zero or negative") {
            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.post("/api/v1/agent-types/create") {
                    contentType(ContentType.Application.Json)
                    setBody(validCreateBody.replace("8192", "0"))
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "maxTokens"
            }
        }

        it("returns 400 when temperature is out of range") {
            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.post("/api/v1/agent-types/create") {
                    contentType(ContentType.Application.Json)
                    setBody(validCreateBody.replace("0.7", "1.5"))
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "temperature"
            }
        }

        it("returns 400 when dockerImage is blank") {
            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.post("/api/v1/agent-types/create") {
                    contentType(ContentType.Application.Json)
                    setBody(validCreateBody.replace("\"bigboote/agent:latest\"", "\"\""))
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "dockerImage"
            }
        }

        it("returns 400 when spawnStrategy is blank") {
            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.post("/api/v1/agent-types/create") {
                    contentType(ContentType.Application.Json)
                    setBody(validCreateBody.replace("\"DOCKER\"", "\"\""))
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "spawnStrategy"
            }
        }

        it("returns 400 when id does not have the agenttype: prefix") {
            coEvery { commandHandler.handle(any<com.bigboote.domain.commands.AgentTypeCommand.CreateAgentType>()) } returns agentTypeId

            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.post("/api/v1/agent-types/create") {
                    contentType(ContentType.Application.Json)
                    setBody(validCreateBody.replace("\"agenttype:lead-engineer\"", "\"agt:lead-engineer\""))
                }

                // AgentTypeId constructor throws IllegalArgumentException for invalid prefix;
                // parseAgentTypeId wraps it into a ValidationException (400).
                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "agentTypeId"
            }
        }
    }

    // ---- GET /api/v1/agent-types ----

    describe("GET /api/v1/agent-types") {

        it("returns 200 with empty agentTypes list when none exist") {
            coEvery { readRepo.list() } returns emptyList()

            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.get("/api/v1/agent-types")

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldContain "\"agentTypes\":[]"
            }
        }

        it("returns 200 with populated list") {
            coEvery { readRepo.list() } returns listOf(sampleRow)

            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.get("/api/v1/agent-types")

                response.status shouldBe HttpStatusCode.OK
                val body = response.bodyAsText()
                body shouldContain "\"name\":\"Lead Engineer\""
                body shouldContain "\"model\":\"claude-opus-4-6\""
                body shouldContain "\"agentTypeId\":\"agenttype:lead-engineer\""
                body shouldContain "\"maxTokens\":8192"
                // updatedAt should be null / absent in the response
                body shouldNotContain "\"updatedAt\":\"20"
            }
        }
    }

    // ---- GET /api/v1/agent-types/{agentTypeId} ----

    describe("GET /api/v1/agent-types/{agentTypeId}") {

        it("returns 200 with full detail when found") {
            coEvery { readRepo.get(agentTypeId) } returns sampleRow

            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.get("/api/v1/agent-types/${agentTypeId.value}")

                response.status shouldBe HttpStatusCode.OK
                val body = response.bodyAsText()
                body shouldContain "\"name\":\"Lead Engineer\""
                body shouldContain "\"systemPrompt\":\"You are a lead software engineer.\""
                body shouldContain "\"temperature\":0.7"
                body shouldContain "\"tools\":[\"bash\",\"read_file\"]"
                body shouldContain "\"dockerImage\":\"bigboote/agent:latest\""
                body shouldContain "\"spawnStrategy\":\"DOCKER\""
            }
        }

        it("returns 404 when agentType is not found in read model") {
            coEvery { readRepo.get(agentTypeId) } returns null

            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.get("/api/v1/agent-types/${agentTypeId.value}")

                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        it("returns 400 when agentTypeId path param lacks the agenttype: prefix") {
            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.get("/api/v1/agent-types/not-a-valid-id")

                // AgentTypeId constructor rejects values without "agenttype:" prefix;
                // parseAgentTypeId wraps it into a 400 ValidationException.
                response.status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    // ---- POST /api/v1/agent-types/{agentTypeId}/update ----

    describe("POST /api/v1/agent-types/{agentTypeId}/update") {

        it("returns 200 with agentTypeId and updatedAt on success") {
            coJustRun { commandHandler.handle(any<com.bigboote.domain.commands.AgentTypeCommand.UpdateAgentType>()) }

            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.post("/api/v1/agent-types/${agentTypeId.value}/update") {
                    contentType(ContentType.Application.Json)
                    setBody("""{ "name": "Senior Lead Engineer" }""")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.bodyAsText()
                body shouldContain "\"agentTypeId\":\"agenttype:lead-engineer\""
                body shouldContain "\"updatedAt\""
            }
        }

        it("returns 200 when updating only modelParams") {
            coJustRun { commandHandler.handle(any<com.bigboote.domain.commands.AgentTypeCommand.UpdateAgentType>()) }

            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.post("/api/v1/agent-types/${agentTypeId.value}/update") {
                    contentType(ContentType.Application.Json)
                    setBody("""{ "modelParams": { "maxTokens": 16384 } }""")
                }

                response.status shouldBe HttpStatusCode.OK
            }
        }

        it("returns 200 when updating tools to an empty list") {
            coJustRun { commandHandler.handle(any<com.bigboote.domain.commands.AgentTypeCommand.UpdateAgentType>()) }

            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.post("/api/v1/agent-types/${agentTypeId.value}/update") {
                    contentType(ContentType.Application.Json)
                    setBody("""{ "tools": [] }""")
                }

                response.status shouldBe HttpStatusCode.OK
            }
        }

        it("returns 404 when the agentType does not exist") {
            coEvery {
                commandHandler.handle(any<com.bigboote.domain.commands.AgentTypeCommand.UpdateAgentType>())
            } throws DomainException(DomainError.AgentTypeNotFound(agentTypeId))

            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.post("/api/v1/agent-types/${agentTypeId.value}/update") {
                    contentType(ContentType.Application.Json)
                    setBody("""{ "name": "Ghost" }""")
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        it("returns 400 when agentTypeId path param is invalid") {
            testApplication {
                application { configureServer() }
                val client = authenticatedClient()
                val response = client.post("/api/v1/agent-types/invalid-id/update") {
                    contentType(ContentType.Application.Json)
                    setBody("""{ "name": "Whatever" }""")
                }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }
    }
})

// ---- test helpers ----

/**
 * Creates a test HTTP client pre-configured with the `Authorization: Bearer test-token`
 * header so that all requests pass the Phase 7 public-api auth check without
 * manually setting the header on every call.
 *
 * Uses [StubBearerTokenValidator] which accepts any non-blank token, so
 * "test-token" is always valid.
 */
private fun ApplicationTestBuilder.authenticatedClient() = createClient {
    defaultRequest {
        headers.append(HttpHeaders.Authorization, "Bearer test-token")
    }
}
