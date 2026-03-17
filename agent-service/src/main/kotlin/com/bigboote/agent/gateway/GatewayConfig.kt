package com.bigboote.agent.gateway

/**
 * Configuration for the Agent Gateway API connection, read from environment variables.
 *
 * @property gatewayUrl Base URL for the Agent Gateway API (e.g., http://coordinator:8080/internal/v1)
 * @property gatewayToken X-Gateway-Token for authenticating with the Agent Gateway API
 */
data class GatewayConfig(
    val gatewayUrl: String,
    val gatewayToken: String,
) {
    companion object {
        fun fromEnvironment(): GatewayConfig = GatewayConfig(
            gatewayUrl = System.getenv("BIGBOOTE_GATEWAY_URL")
                ?: "http://localhost:8080/internal/v1",
            gatewayToken = System.getenv("BIGBOOTE_GATEWAY_TOKEN")
                ?: "dev-gateway-token",
        )
    }
}
