package com.bigboote.coordinator

import com.bigboote.coordinator.koin.*
import com.bigboote.coordinator.projections.ProjectionRunner
import com.bigboote.coordinator.projections.db.EffortTable
import com.bigboote.infra.config.BigbooteConfig
import com.bigboote.infra.db.DatabaseFactory
import com.bigboote.infra.koin.sharedInfraModule
import io.ktor.server.application.Application
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
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

    // Connect to Postgres — DatabaseFactory.connect() initializes the pool.
    databaseFactory.connect()
    logger.info("Postgres connection established at {}", config.database.jdbcUrl)

    // Create or migrate Postgres schema for all Phase 5 tables.
    // SchemaUtils.createMissingTablesAndColumns is idempotent — safe on every restart.
    databaseFactory.createTables(EffortTable)
    logger.info("Database schema initialised")

    // KurrentDB client is eagerly created by Koin; log confirmation
    logger.info("KurrentDB connection configured at {}", config.kurrent.connectionString)

    // Start projections — must happen after DB schema is ready so that start()
    // can read known effort IDs from EffortTable for catch-up subscriptions.
    val projectionRunner = koin.get<ProjectionRunner>()
    projectionRunner.start()
    logger.info("ProjectionRunner started")

    // Graceful shutdown: stop projection subscriptions and close the DB pool
    // when the JVM receives SIGTERM (e.g. docker stop, k8s scale-down).
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutdown hook: stopping projections and closing DB pool...")
        projectionRunner.stop()
        databaseFactory.close()
        logger.info("Shutdown complete")
    })

    embeddedServer(Netty, port = 8080, module = Application::configureServer)
        .start(wait = true)
}
