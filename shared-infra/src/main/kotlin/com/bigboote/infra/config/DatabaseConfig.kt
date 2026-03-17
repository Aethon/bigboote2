package com.bigboote.infra.config

/**
 * Configuration for the Postgres connection pool.
 *
 * @param jdbcUrl JDBC connection URL, e.g. "jdbc:postgresql://localhost:5432/bigboote"
 * @param username Database user
 * @param password Database password
 * @param maxPoolSize Maximum number of connections in the HikariCP pool (default 10)
 */
data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val maxPoolSize: Int = 10,
)
