package com.bigboote.coordinator.proxy.spawn

import com.bigboote.coordinator.proxy.agent.FlyMachineAgentProxy
import com.bigboote.domain.values.AgentId
import com.bigboote.domain.values.AgentTypeId
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.EffortId
import com.bigboote.infra.config.FlyConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Unit tests for [FlyMachineSpawnStrategy].
 *
 * The Fly Machines REST API is simulated via Ktor's [MockEngine], so no real
 * network connections or Fly accounts are required.
 *
 * Verified behaviours:
 *  - [spawn] POSTs to the correct Machines API endpoint.
 *  - [spawn] passes the docker image, region, and env vars in the request body.
 *  - [spawn] waits for the machine to reach "started" state via the wait endpoint.
 *  - [spawn] returns a [FlyMachineAgentProxy] with the correct Fly private-network
 *    control URL and identity fields.
 *  - [spawn] throws when the Machines API returns a non-2xx status.
 *  - [teardown] calls DELETE on the correct machine URL with `?force=true`.
 *  - [teardown] is a no-op when the agentId is unknown.
 *
 * See Architecture doc Section 20.1.
 */
class FlyMachineSpawnStrategyTest : DescribeSpec({

    // ---- fixtures ----------------------------------------------------------------

    val flyConfig = FlyConfig(
        apiToken = "fly-token-test",
        appName  = "bigboote-test",
        region   = "iad",
        org      = "personal",
    )

    val agentId     = AgentId("agent:fly-test-001")
    val effortId    = EffortId("effort:fly-test-001")
    val agentTypeId = AgentTypeId.of("fly-test-type")
    val collab      = CollaboratorName.Individual("fly-agent")

    val spawnConfig = SpawnConfig(
        agentId          = agentId,
        effortId         = effortId,
        agentTypeId      = agentTypeId,
        collaboratorName = collab,
        gatewayToken     = "gw-token-fly",
        agentToken       = "agent-token-fly",
        dockerImage      = "bigboote/agent-service:latest",
    )

    val machineId = "e784319a6b3789"

    // ---- helpers -----------------------------------------------------------------

    /** Read the body bytes from an [OutgoingContent] (works for ByteArrayContent / TextContent). */
    fun OutgoingContent.readBodyText(): String =
        (this as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString() ?: ""

    /** Build a happy-path [HttpClient] backed by MockEngine: handles create + wait + delete. */
    fun happyClient(
        capturedRequests: MutableList<HttpRequestData> = mutableListOf(),
        createMachineId: String = machineId,
    ): HttpClient = HttpClient(
        MockEngine { request ->
            capturedRequests += request
            val path = request.url.encodedPath
            when {
                request.method == HttpMethod.Post && path.endsWith("/machines") ->
                    respond(
                        content = """{"id":"$createMachineId","state":"created"}""",
                        status  = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                request.method == HttpMethod.Get && path.contains("/wait") ->
                    respond(
                        content = """{}""",
                        status  = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                request.method == HttpMethod.Delete && path.contains("/machines/") ->
                    respond(
                        content = """{}""",
                        status  = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                else -> error("Unexpected request: ${request.method} ${request.url.encodedPath}")
            }
        }
    ) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    // ---- tests -------------------------------------------------------------------

    describe("FlyMachineSpawnStrategy.spawn") {

        it("returns a FlyMachineAgentProxy with the correct identity fields") {
            val strategy = FlyMachineSpawnStrategy(flyConfig, happyClient())

            val proxy = strategy.spawn(spawnConfig) as FlyMachineAgentProxy

            proxy.agentId          shouldBe agentId
            proxy.effortId         shouldBe effortId
            proxy.collaboratorName shouldBe collab
        }

        it("builds the Fly private-network control URL from the machine ID") {
            val strategy = FlyMachineSpawnStrategy(flyConfig, happyClient())

            val proxy = strategy.spawn(spawnConfig) as FlyMachineAgentProxy

            proxy.controlUrl shouldBe
                "http://$machineId.vm.${flyConfig.appName}.internal:8081/control/v1"
        }

        it("POSTs to the correct Fly Machines API path") {
            val captured = mutableListOf<HttpRequestData>()
            val strategy = FlyMachineSpawnStrategy(flyConfig, happyClient(captured))

            strategy.spawn(spawnConfig)

            val createReq = captured.first { it.method == HttpMethod.Post }
            createReq.url.encodedPath shouldBe "/v1/apps/${flyConfig.appName}/machines"
        }

        it("includes the docker image in the create-machine request body") {
            val captured = mutableListOf<HttpRequestData>()
            val strategy = FlyMachineSpawnStrategy(flyConfig, happyClient(captured))

            strategy.spawn(spawnConfig)

            val createReq = captured.first { it.method == HttpMethod.Post }
            val body = createReq.body.readBodyText()
            body shouldContain spawnConfig.dockerImage
        }

        it("includes the gateway and agent tokens as env vars in the request body") {
            val captured = mutableListOf<HttpRequestData>()
            val strategy = FlyMachineSpawnStrategy(flyConfig, happyClient(captured))

            strategy.spawn(spawnConfig)

            val body = captured.first { it.method == HttpMethod.Post }.body.readBodyText()
            body shouldContain spawnConfig.gatewayToken
            body shouldContain spawnConfig.agentToken
        }

        it("includes the configured region in the create-machine request body") {
            val captured = mutableListOf<HttpRequestData>()
            val strategy = FlyMachineSpawnStrategy(flyConfig, happyClient(captured))

            strategy.spawn(spawnConfig)

            val body = captured.first { it.method == HttpMethod.Post }.body.readBodyText()
            body shouldContain flyConfig.region
        }

        it("sends Authorization Bearer header on all requests") {
            val captured = mutableListOf<HttpRequestData>()
            val strategy = FlyMachineSpawnStrategy(flyConfig, happyClient(captured))

            strategy.spawn(spawnConfig)

            captured.forEach { req ->
                req.headers[HttpHeaders.Authorization] shouldBe "Bearer ${flyConfig.apiToken}"
            }
        }

        it("calls the wait endpoint with state=started and the correct machine ID in the path") {
            val captured = mutableListOf<HttpRequestData>()
            val strategy = FlyMachineSpawnStrategy(flyConfig, happyClient(captured))

            strategy.spawn(spawnConfig)

            val waitReq = captured.first { it.url.encodedPath.contains("/wait") }
            waitReq.url.parameters["state"]  shouldBe "started"
            waitReq.url.encodedPath shouldContain machineId
        }

        it("throws when the Fly Machines API returns a non-2xx status on machine create") {
            val errorClient = HttpClient(MockEngine {
                respond(
                    content = """{"error":"app not found"}""",
                    status  = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }

            val strategy = FlyMachineSpawnStrategy(flyConfig, errorClient)

            shouldThrow<Exception> { strategy.spawn(spawnConfig) }
        }
    }

    describe("FlyMachineSpawnStrategy.teardown") {

        it("sends DELETE with force=true to the correct machine URL") {
            val captured = mutableListOf<HttpRequestData>()
            val strategy = FlyMachineSpawnStrategy(flyConfig, happyClient(captured))

            strategy.spawn(spawnConfig)
            captured.clear()

            strategy.teardown(agentId)

            val deleteReq = captured.single { it.method == HttpMethod.Delete }
            deleteReq.url.encodedPath shouldContain "/machines/$machineId"
            deleteReq.url.parameters["force"] shouldBe "true"
            deleteReq.headers[HttpHeaders.Authorization] shouldBe "Bearer ${flyConfig.apiToken}"
        }

        it("does not make any HTTP calls for an unknown agentId") {
            val captured = mutableListOf<HttpRequestData>()
            val strategy = FlyMachineSpawnStrategy(flyConfig, happyClient(captured))

            // Teardown without a prior spawn — no machine ID is recorded
            strategy.teardown(AgentId("agent:unknown-xyz"))

            captured.isEmpty().shouldBeTrue()
        }
    }
})
