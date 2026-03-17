package com.bigboote.coordinator

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import org.koin.ktor.plugin.Koin

fun Application.configureServer() {
    install(ContentNegotiation) { json() }
    install(Koin) { /* already started in main */ }

    // TODO: install auth plugin
    // TODO: install WebSockets
    // TODO: install SSE

    routing {
        // routes mounted in Phase 3+
    }
}
