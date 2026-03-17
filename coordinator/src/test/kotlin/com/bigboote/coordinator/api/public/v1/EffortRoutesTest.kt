package com.bigboote.coordinator.api.public.v1

import com.bigboote.coordinator.aggregates.effort.EffortCommandHandler
import com.bigboote.coordinator.configureServer
import com.bigboote.coordinator.projections.EffortSummaryProjection
import com.bigboote.coordinator.projections.repositories.EffortReadRepository
import com.bigboote.coordinator.projections.repositories.EffortRow
import com.bigboote.domain.aggregates.EffortStatus
import com.bigboote.domain.commands.EffortCommand.*
import com.bigboote.domain.errors.DomainError
import com.bigboote.domain.values.*
import com.bigboote.coordinator.api.error.DomainException
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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
 * Integration tests for [effortRoutes] that exercise the full Ktor request
 * pipeline with mocked dependencies.
 *
 * Infrastructure dependencies (EffortCommandHandler, EffortSummaryProjection,
 * EffortReadRepository) are replaced with MockK stubs so that no KurrentDB or
 * Postgres connection is required.
 *
 * Test coverage:
 *  - POST /api/v1/efforts/create — happy path + validation failures
 *  - GET  /api/v1/efforts — list all and filtered by status
 *  - GET  /api/v1/efforts/{effortId} — found + not found
 *  - POST /api/v1/efforts/{effortId}/start|pause|resume|close — happy paths
 */
class EffortRoutesTest : DescribeSpec({

    // ---- shared fixtures ----

    val effortId = EffortId("effort:test-abc")
    val now = Clock.System.now()

    // CollaboratorName.Individual takes a bare name (no @ prefix); toString() adds "@".
    val humanLead = CollaboratorSpec(
        name = CollaboratorName.Individual("lead-dev"),
        type = CollaboratorType.HUMAN,
        agentTypeId = null,
        isLead = true,
    )

    val sampleRow = EffortRow(
        effortId = effortId,
        name = "Test Effort",
        goal = "Deliver something great",
        status = EffortStatus.CREATED,
        leadName = "@lead-dev",
        collaborators = listOf(humanLead),
        createdAt = now,
        startedAt = null,
        closedAt = null,
    )

    // ---- mocks + Koin lifecycle ----

    lateinit var commandHandler: EffortCommandHandler
    lateinit var projection: EffortSummaryProjection
    lateinit var readRepo: EffortReadRepository

    beforeEach {
        commandHandler = mockk()
        projection = mockk(relaxed = true)   // trackEffort/stop are side-effects we don't assert here
        readRepo = mockk()

        try { stopKoin() } catch (_: Exception) { }
        startKoin {
            modules(module {
                single { commandHandler }
                single { projection }
                single { readRepo }
            })
        }
    }

    afterEach {
        try { stopKoin() } catch (_: Exception) { }
    }

    // ---- POST /api/v1/efforts/create ----

    describe("POST /api/v1/efforts/create") {

        it("returns 201 Created with effortId and status=created for a valid HUMAN-lead request") {
            coEvery { commandHandler.handle(any<CreateEffort>()) } returns effortId

            testApplication {
                application { configureServer() }
                val response = client.post("/api/v1/efforts/create") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "name": "Test Effort",
                          "goal": "Deliver something great",
                          "collaborators": [
                            { "name": "lead-dev", "type": "HUMAN", "isLead": true }
                          ]
                        }
                        """.trimIndent()
                    )
                }

                response.status shouldBe HttpStatusCode.Created
                val body = response.bodyAsText()
                body shouldContain "\"effortId\""
                body shouldContain "\"status\":\"created\""
                body shouldContain "\"createdAt\""
            }
        }

        it("returns 201 Created when an AGENT collaborator correctly specifies agentTypeId") {
            coEvery { commandHandler.handle(any<CreateEffort>()) } returns effortId

            testApplication {
                application { configureServer() }
                val response = client.post("/api/v1/efforts/create") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "name": "Agent Effort",
                          "goal": "Run agents",
                          "collaborators": [
                            { "name": "lead-dev", "type": "HUMAN", "isLead": true },
                            { "name": "code-bot", "type": "AGENT", "agentTypeId": "agenttype:coder", "isLead": false }
                          ]
                        }
                        """.trimIndent()
                    )
                }

                response.status shouldBe HttpStatusCode.Created
            }
        }

        it("returns 400 when name is blank") {
            testApplication {
                application { configureServer() }
                val response = client.post("/api/v1/efforts/create") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "name": "   ",
                          "goal": "Some goal",
                          "collaborators": [{ "name": "lead", "type": "HUMAN", "isLead": true }]
                        }
                        """.trimIndent()
                    )
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "name"
            }
        }

        it("returns 400 when goal is blank") {
            testApplication {
                application { configureServer() }
                val response = client.post("/api/v1/efforts/create") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "name": "Valid Name",
                          "goal": "",
                          "collaborators": [{ "name": "lead", "type": "HUMAN", "isLead": true }]
                        }
                        """.trimIndent()
                    )
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "goal"
            }
        }

        it("returns 400 when collaborators list is empty") {
            testApplication {
                application { configureServer() }
                val response = client.post("/api/v1/efforts/create") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "name": "Valid Name",
                          "goal": "Some goal",
                          "collaborators": []
                        }
                        """.trimIndent()
                    )
                }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        it("returns 400 when no collaborator has isLead=true") {
            testApplication {
                application { configureServer() }
                val response = client.post("/api/v1/efforts/create") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "name": "Valid Name",
                          "goal": "Some goal",
                          "collaborators": [
                            { "name": "member", "type": "HUMAN", "isLead": false }
                          ]
                        }
                        """.trimIndent()
                    )
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "isLead"
            }
        }

        it("returns 400 when more than one collaborator has isLead=true") {
            testApplication {
                application { configureServer() }
                val response = client.post("/api/v1/efforts/create") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "name": "Valid Name",
                          "goal": "Some goal",
                          "collaborators": [
                            { "name": "lead1", "type": "HUMAN", "isLead": true },
                            { "name": "lead2", "type": "HUMAN", "isLead": true }
                          ]
                        }
                        """.trimIndent()
                    )
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "isLead"
            }
        }

        it("returns 400 when AGENT collaborator is missing agentTypeId") {
            testApplication {
                application { configureServer() }
                val response = client.post("/api/v1/efforts/create") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "name": "Valid Name",
                          "goal": "Some goal",
                          "collaborators": [
                            { "name": "bot", "type": "AGENT", "isLead": true }
                          ]
                        }
                        """.trimIndent()
                    )
                }

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "agentTypeId"
            }
        }
    }

    // ---- GET /api/v1/efforts ----

    describe("GET /api/v1/efforts") {

        it("returns 200 with empty list when no efforts exist") {
            coEvery { readRepo.list(null) } returns emptyList()

            testApplication {
                application { configureServer() }
                val response = client.get("/api/v1/efforts")

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldContain "\"efforts\":[]"
            }
        }

        it("returns 200 with efforts in list") {
            coEvery { readRepo.list(null) } returns listOf(sampleRow)

            testApplication {
                application { configureServer() }
                val response = client.get("/api/v1/efforts")

                response.status shouldBe HttpStatusCode.OK
                val body = response.bodyAsText()
                body shouldContain "\"name\":\"Test Effort\""
                body shouldContain "\"status\":\"created\""
                body shouldContain "\"lead\":\"lead-dev\""   // bare name without @
            }
        }

        it("returns 200 filtered by status=active") {
            coEvery { readRepo.list(EffortStatus.ACTIVE) } returns emptyList()

            testApplication {
                application { configureServer() }
                val response = client.get("/api/v1/efforts?status=active")

                response.status shouldBe HttpStatusCode.OK
            }
        }

        it("returns 400 for an unrecognised status parameter") {
            testApplication {
                application { configureServer() }
                val response = client.get("/api/v1/efforts?status=bogus")

                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "bogus"
            }
        }
    }

    // ---- GET /api/v1/efforts/{effortId} ----

    describe("GET /api/v1/efforts/{effortId}") {

        it("returns 200 with effort detail when found") {
            coEvery { readRepo.get(effortId) } returns sampleRow

            testApplication {
                application { configureServer() }
                val response = client.get("/api/v1/efforts/${effortId.value}")

                response.status shouldBe HttpStatusCode.OK
                val body = response.bodyAsText()
                body shouldContain "\"name\":\"Test Effort\""
                body shouldContain "\"goal\":\"Deliver something great\""
                body shouldContain "\"status\":\"created\""
                // lead name should be bare (no @ prefix)
                body shouldContain "\"lead\":\"lead-dev\""
                body shouldNotContain "\"lead\":\"@lead-dev\""
            }
        }

        it("returns 404 when effort is not found") {
            coEvery { readRepo.get(effortId) } returns null

            testApplication {
                application { configureServer() }
                val response = client.get("/api/v1/efforts/${effortId.value}")

                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        it("returns 400 when effortId path param is invalid") {
            testApplication {
                application { configureServer() }
                val response = client.get("/api/v1/efforts/not-a-valid-id")

                // EffortId constructor throws IllegalArgumentException for non-"effort:..." IDs,
                // which the route converts to a 400 ValidationException.
                response.status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    // ---- POST /api/v1/efforts/{effortId}/start ----

    describe("POST /api/v1/efforts/{effortId}/start") {

        it("returns 200 with status=active on success") {
            coJustRun { commandHandler.handle(any<StartEffort>()) }

            testApplication {
                application { configureServer() }
                val response = client.post("/api/v1/efforts/${effortId.value}/start")

                response.status shouldBe HttpStatusCode.OK
                val body = response.bodyAsText()
                body shouldContain "\"status\":\"active\""
                body shouldContain "\"effortId\":\"${effortId.value}\""
            }
        }

        it("returns 422 when the effort is not in a startable state") {
            coEvery { commandHandler.handle(any<StartEffort>()) } throws
                DomainException(DomainError.InvalidEffortTransition(effortId, EffortStatus.ACTIVE, EffortStatus.ACTIVE))

            testApplication {
                application { configureServer() }
                val response = client.post("/api/v1/efforts/${effortId.value}/start")

                response.status shouldBe HttpStatusCode.UnprocessableEntity
            }
        }
    }

    // ---- POST /api/v1/efforts/{effortId}/pause ----

    describe("POST /api/v1/efforts/{effortId}/pause") {

        it("returns 200 with status=paused on success") {
            coJustRun { commandHandler.handle(any<PauseEffort>()) }

            testApplication {
                application { configureServer() }
                val response = client.post("/api/v1/efforts/${effortId.value}/pause")

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldContain "\"status\":\"paused\""
            }
        }
    }

    // ---- POST /api/v1/efforts/{effortId}/resume ----

    describe("POST /api/v1/efforts/{effortId}/resume") {

        it("returns 200 with status=active on success") {
            coJustRun { commandHandler.handle(any<ResumeEffort>()) }

            testApplication {
                application { configureServer() }
                val response = client.post("/api/v1/efforts/${effortId.value}/resume")

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldContain "\"status\":\"active\""
            }
        }
    }

    // ---- POST /api/v1/efforts/{effortId}/close ----

    describe("POST /api/v1/efforts/{effortId}/close") {

        it("returns 200 with status=closed on success") {
            coJustRun { commandHandler.handle(any<CloseEffort>()) }

            testApplication {
                application { configureServer() }
                val response = client.post("/api/v1/efforts/${effortId.value}/close")

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldContain "\"status\":\"closed\""
            }
        }

        it("returns 422 when effort is already closed") {
            coEvery { commandHandler.handle(any<CloseEffort>()) } throws
                DomainException(DomainError.EffortAlreadyClosed(effortId))

            testApplication {
                application { configureServer() }
                val response = client.post("/api/v1/efforts/${effortId.value}/close")

                response.status shouldBe HttpStatusCode.UnprocessableEntity
            }
        }
    }
})
