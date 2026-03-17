package com.bigboote.infra.config

/**
 * Configuration for the KurrentDB connection.
 *
 * @param connectionString KurrentDB connection string, e.g. "esdb://localhost:2113?tls=false"
 */
data class KurrentConfig(
    val connectionString: String,
)
