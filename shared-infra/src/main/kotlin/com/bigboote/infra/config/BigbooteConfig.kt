package com.bigboote.infra.config

/**
 * Top-level configuration for a Bigboote service instance.
 * Loaded from environment variables with sensible development defaults.
 */
data class BigbooteConfig(
    val kurrent: KurrentConfig,
    val database: DatabaseConfig,
    val s3: S3Config,
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
            s3 = S3Config(
                // null → real AWS (IAM role / default credential chain).
                // Set BIGBOOTE_S3_ENDPOINT=http://localhost:4566 for LocalStack,
                // or http://localhost:9000 for MinIO in local development.
                endpoint        = System.getenv("BIGBOOTE_S3_ENDPOINT"),
                region          = System.getenv("BIGBOOTE_S3_REGION")          ?: "us-east-1",
                bucket          = System.getenv("BIGBOOTE_S3_BUCKET")          ?: "bigboote-documents",
                accessKeyId     = System.getenv("BIGBOOTE_S3_ACCESS_KEY"),
                secretAccessKey = System.getenv("BIGBOOTE_S3_SECRET_KEY"),
            ),
        )
    }
}
