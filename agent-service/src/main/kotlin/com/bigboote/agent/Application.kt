package com.bigboote.agent

import io.ktor.server.application.Application
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.koin.core.context.startKoin
import com.bigboote.agent.koin.AgentServiceModule

fun main() {
    startKoin {
        modules(AgentServiceModule)
    }

    val port = System.getenv("BIGBOOTE_CONTROL_PORT")?.toInt() ?: 8081
    embeddedServer(Netty, port = port, module = Application::configureServer)
        .start(wait = true)
}
