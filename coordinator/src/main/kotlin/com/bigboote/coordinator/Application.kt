package com.bigboote.coordinator

import com.bigboote.infra.config.BigbooteConfig
import com.bigboote.infra.db.DatabaseFactory
import com.bigboote.infra.koin.sharedInfraModule
import io.ktor.server.application.Application
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import com.bigboote.coordinator.koin.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.bigboote.coordinator.Application")

fun main() {
    startKoin {
        modules(
            sharedInfraModule,
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

    // Initialize infrastructure connections on startup
    val koin = GlobalContext.get()
    val config = koin.get<BigbooteConfig>()
    val databaseFactory = koin.get<DatabaseFactory>()

    // Connect to Postgres — DatabaseFactory.connect() initializes the pool
    // and logs internally; we discard the returned Database reference here
    // because Exposed's Database is not on coordinator's compile classpath.
    databaseFactory.connect()
    logger.info("Postgres connection established at {}", config.database.jdbcUrl)

    // KurrentDB client is eagerly created by Koin; log confirmation
    logger.info("KurrentDB connection configured at {}", config.kurrent.connectionString)

    embeddedServer(Netty, port = 8080, module = Application::configureServer)
        .start(wait = true)
}
