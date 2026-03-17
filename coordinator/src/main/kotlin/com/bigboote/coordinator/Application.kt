package com.bigboote.coordinator

import io.ktor.server.application.Application
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.koin.core.context.startKoin
import com.bigboote.coordinator.koin.*

fun main() {
    startKoin {
        modules(
            InfrastructureModule,
            AuthModule,
            DomainModule,
            ProjectionModule,
            ReactorModule,
            ProxyModule,
            MessagingModule,
            ApiModule,
        )
    }

    embeddedServer(Netty, port = 8080, module = Application::configureServer)
        .start(wait = true)
}
