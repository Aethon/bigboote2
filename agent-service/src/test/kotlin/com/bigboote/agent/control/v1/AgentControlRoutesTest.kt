package com.bigboote.agent.control.v1

import com.bigboote.agent.configureServer
import com.bigboote.agent.koin.AgentServiceModule
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

class AgentControlRoutesTest : DescribeSpec({

    beforeEach {
        try { stopKoin() } catch (_: Exception) { }
        startKoin { modules(AgentServiceModule) }
    }

    afterEach {
        try { stopKoin() } catch (_: Exception) { }
    }

    describe("GET /control/v1/status") {
        it("returns 200 with starting state before start is called") {
            testApplication {
                application { configureServer() }
                val response = client.get("/control/v1/status")
                response.status shouldBe HttpStatusCode.OK
                val body = response.bodyAsText()
                body shouldContain "\"loopState\":\"starting\""
            }
        }
    }

    describe("POST /control/v1/start") {
        it("returns 200 with started=true and updates loopState to running") {
            testApplication {
                application { configureServer() }

                val startBody = """
                    {
                        "effortId": "effort:test123",
                        "instanceId": "agent:test456",
                        "agentTypeId": "agenttype:lead-engineer",
                        "agentGatewayUrl": "http://coordinator:8080/internal/v1"
                    }
                """.trimIndent()

                val startResponse = client.post("/control/v1/start") {
                    contentType(ContentType.Application.Json)
                    setBody(startBody)
                }
                startResponse.status shouldBe HttpStatusCode.OK
                val startText = startResponse.bodyAsText()
                startText shouldContain "\"started\":true"
                startText shouldContain "\"instanceId\":\"agent:test456\""

                // Verify status reflects running state
                val statusResponse = client.get("/control/v1/status")
                val statusText = statusResponse.bodyAsText()
                statusText shouldContain "\"loopState\":\"running\""
                statusText shouldContain "\"instanceId\":\"agent:test456\""
                statusText shouldContain "\"effortId\":\"effort:test123\""
            }
        }
    }

    describe("POST /control/v1/pause") {
        it("returns 200 and transitions loopState to paused") {
            testApplication {
                application { configureServer() }

                // Start first
                client.post("/control/v1/start") {
                    contentType(ContentType.Application.Json)
                    setBody("""
                        {
                            "effortId": "effort:test123",
                            "instanceId": "agent:test456",
                            "agentTypeId": "agenttype:lead-engineer",
                            "agentGatewayUrl": "http://coordinator:8080/internal/v1"
                        }
                    """.trimIndent())
                }

                val pauseResponse = client.post("/control/v1/pause")
                pauseResponse.status shouldBe HttpStatusCode.OK
                pauseResponse.bodyAsText() shouldContain "\"success\":true"

                val statusResponse = client.get("/control/v1/status")
                statusResponse.bodyAsText() shouldContain "\"loopState\":\"paused\""
            }
        }
    }

    describe("POST /control/v1/resume") {
        it("returns 200 and transitions loopState back to running") {
            testApplication {
                application { configureServer() }

                // Start then pause
                client.post("/control/v1/start") {
                    contentType(ContentType.Application.Json)
                    setBody("""
                        {
                            "effortId": "effort:test123",
                            "instanceId": "agent:test456",
                            "agentTypeId": "agenttype:lead-engineer",
                            "agentGatewayUrl": "http://coordinator:8080/internal/v1"
                        }
                    """.trimIndent())
                }
                client.post("/control/v1/pause")

                val resumeResponse = client.post("/control/v1/resume")
                resumeResponse.status shouldBe HttpStatusCode.OK
                resumeResponse.bodyAsText() shouldContain "\"success\":true"

                val statusResponse = client.get("/control/v1/status")
                statusResponse.bodyAsText() shouldContain "\"loopState\":\"running\""
            }
        }
    }

    describe("POST /control/v1/stop") {
        it("returns 200 and transitions loopState to stopped") {
            testApplication {
                application { configureServer() }

                client.post("/control/v1/start") {
                    contentType(ContentType.Application.Json)
                    setBody("""
                        {
                            "effortId": "effort:test123",
                            "instanceId": "agent:test456",
                            "agentTypeId": "agenttype:lead-engineer",
                            "agentGatewayUrl": "http://coordinator:8080/internal/v1"
                        }
                    """.trimIndent())
                }

                val stopResponse = client.post("/control/v1/stop")
                stopResponse.status shouldBe HttpStatusCode.OK
                stopResponse.bodyAsText() shouldContain "\"success\":true"

                val statusResponse = client.get("/control/v1/status")
                statusResponse.bodyAsText() shouldContain "\"loopState\":\"stopped\""
            }
        }
    }
})
