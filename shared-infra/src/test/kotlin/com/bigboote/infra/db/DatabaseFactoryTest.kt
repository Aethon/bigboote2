package com.bigboote.infra.db

import com.bigboote.infra.config.DatabaseConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Integration test for DatabaseFactory using TestContainers Postgres.
 * Verifies connection pool setup and table creation.
 */
class DatabaseFactoryTest : StringSpec({

    val postgres = PostgreSQLContainer("postgres:16-alpine")

    lateinit var factory: DatabaseFactory

    beforeSpec {
        postgres.start()
        factory = DatabaseFactory(
            DatabaseConfig(
                jdbcUrl = postgres.jdbcUrl,
                username = postgres.username,
                password = postgres.password,
                maxPoolSize = 2,
            )
        )
        factory.connect()
    }

    afterSpec {
        factory.close()
        postgres.stop()
    }

    "connects to Postgres without error" {
        // If we got here, connect() succeeded. Verify with a simple query.
        transaction {
            exec("SELECT 1") shouldNotBe null
        }
    }

    "creates tables via SchemaUtils" {
        // Define a test table
        var testTable = object : Table("infra_test") {
            val id = integer("id").autoIncrement()
            val name = varchar("name", 128)
            override val primaryKey = PrimaryKey(id)
        }

        factory.createTables(testTable)

        // Verify the table exists and is queryable
        transaction {
            testTable.selectAll().toList() // should not throw
        }
    }

    "dbQuery executes within coroutine context" {
        var coroutineTestTable = object  : Table("coroutine_test") {
            val id = integer("id").autoIncrement()
            val value = varchar("value", 64)
            override val primaryKey = PrimaryKey(id)
        }

        factory.createTables(coroutineTestTable)

        val result = dbQuery {
            coroutineTestTable.selectAll().toList()
        }
        result shouldNotBe null
    }
})
