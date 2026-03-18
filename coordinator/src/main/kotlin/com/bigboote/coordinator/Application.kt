package com.bigboote.coordinator

import com.bigboote.coordinator.koin.*
import com.bigboote.coordinator.projections.ProjectionRunner
import com.bigboote.coordinator.projections.db.AgentTypeTable
import com.bigboote.coordinator.projections.db.ConversationTable
import com.bigboote.coordinator.projections.db.MessageTable
import com.bigboote.coordinator.projections.db.EffortTable
import com.bigboote.coordinator.reactors.ReactorRunner
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

    // Create or migrate Postgres schema for all read-model tables.
    // SchemaUtils.createMissingTablesAndColumns is idempotent — safe on every restart.
    databaseFactory.createTables(EffortTable, AgentTypeTable, ConversationTable, MessageTable)
    logger.info("Database schema initialised")

    // KurrentDB client is eagerly created by Koin; log confirmation
    logger.info("KurrentDB connection configured at {}", config.kurrent.connectionString)

    // Start projections — must happen after DB schema is ready so that start()
    // can read known effort IDs from EffortTable for catch-up subscriptions.
    val projectionRunner = koin.get<ProjectionRunner>()
    projectionRunner.start()
    logger.info("ProjectionRunner started")

    // Start reactors — must happen after projections are running so that the
    // AgentType read model is populated before SpawnReactor tries to look up dockerImage.
    val reactorRunner = koin.get<ReactorRunner>()
    reactorRunner.start()
    logger.info("ReactorRunner started")

    // Graceful shutdown: stop reactors, then projection subscriptions, then close the DB pool.
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutdown hook: stopping reactors, projections, and closing DB pool...")
        reactorRunner.stop()
        projectionRunner.stop()
        databaseFactory.close()
        logger.info("Shutdown complete")
    })

    embeddedServer(Netty, port = 8080, module = Application::configureServer)
        .start(wait = true)
}
