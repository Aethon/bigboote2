package com.bigboote.agent

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import org.koin.ktor.plugin.Koin

fun Application.configureServer() {
    install(ContentNegotiation) { json() }
    install(Koin) { /* already started in main */ }

    routing {
        // Agent Control API routes mounted in later phases
    }
}
