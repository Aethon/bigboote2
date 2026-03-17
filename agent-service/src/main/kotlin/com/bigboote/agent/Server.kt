package com.bigboote.agent

import com.bigboote.agent.control.v1.agentControlRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.ktor.plugin.Koin

fun Application.configureServer() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
    install(Koin) { /* already started in main */ }

    routing {
        agentControlRoutes()
    }
}
