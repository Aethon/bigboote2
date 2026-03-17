package com.bigboote.infra.config

/**
 * Top-level configuration for a Bigboote service instance.
 * Loaded from HOCON (application.conf) or environment variables.
 */
data class BigbooteConfig(
    val kurrent: KurrentConfig,
    val database: DatabaseConfig,
) {
    companion object {
        /**
         * Load configuration from environment variables with sensible defaults
         * for local development.
         */
        fun fromEnvironment(): BigbooteConfig = BigbooteConfig(
            kurrent = KurrentConfig(
                connectionString = System.getenv("BIGBOOTE_KURRENTDB_URL")
                    ?: "esdb://localhost:2113?tls=false",
            ),
            database = DatabaseConfig(
                jdbcUrl = System.getenv("BIGBOOTE_DB_URL")
                    ?: "jdbc:postgresql://localhost:5432/bigboote",
                username = System.getenv("BIGBOOTE_DB_USER")
                    ?: "bigboote",
                password = System.getenv("BIGBOOTE_DB_PASSWORD")
                    ?: "bigboote",
                maxPoolSize = System.getenv("BIGBOOTE_DB_POOL_SIZE")?.toIntOrNull()
                    ?: 10,
            ),
        )
    }
}
