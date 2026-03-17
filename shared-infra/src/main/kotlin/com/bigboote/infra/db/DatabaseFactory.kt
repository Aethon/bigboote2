package com.bigboote.infra.db

import com.bigboote.infra.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Sets up the HikariCP connection pool and Exposed database connection.
 * Call [connect] on startup to initialize, and [createTables] to ensure
 * schema is up to date.
 */
class DatabaseFactory(private val config: DatabaseConfig) {

    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)
    private lateinit var dataSource: HikariDataSource

    /**
     * Initialize the connection pool and connect Exposed.
     */
    fun connect(): Database {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.username
            password = config.password
            maximumPoolSize = config.maxPoolSize
            isAutoCommit = false
            driverClassName = "org.postgresql.Driver"
            validate()
        }

        dataSource = HikariDataSource(hikariConfig)
        val database = Database.connect(dataSource)

        logger.info("Connected to database at {}", config.jdbcUrl)
        return database
    }

    /**
     * Creates missing tables and columns. Called on startup after [connect].
     *
     * @param tables The Exposed Table definitions to create/update.
     */
    fun createTables(vararg tables: Table) {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(*tables)
        }
        logger.info("Database schema updated for {} table(s)", tables.size)
    }

    /**
     * Closes the connection pool. Call on shutdown.
     */
    fun close() {
        if (::dataSource.isInitialized) {
            dataSource.close()
            logger.info("Database connection pool closed")
        }
    }
}
