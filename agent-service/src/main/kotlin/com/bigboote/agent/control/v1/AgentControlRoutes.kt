package com.bigboote.agent.control.v1

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Mounts the Agent Control API routes at /control/v1/{*}.
 *
 * Authentication via X-Agent-Token is stubbed for Phase 8.
 * Phase 7 (Coordinator auth) adds proper token validation when
 * the Coordinator calls into the agent-service.
 */
fun Route.agentControlRoutes() {
    val handler by application.inject<AgentControlHandler>()

    route("/control/v1") {

        get("/status") {
            call.respond(HttpStatusCode.OK, handler.status())
        }

        post("/start") {
            val request = call.receive<StartRequest>()
            val response = handler.start(request)
            call.respond(HttpStatusCode.OK, response)
        }

        post("/pause") {
            val response = handler.pause()
            call.respond(HttpStatusCode.OK, response)
        }

        post("/resume") {
            val response = handler.resume()
            call.respond(HttpStatusCode.OK, response)
        }

        post("/stop") {
            val response = handler.stop()
            call.respond(HttpStatusCode.OK, response)
        }
    }
}
