package com.bigboote.agent.gateway

import io.ktor.client.*
import org.slf4j.LoggerFactory

/**
 * HTTP client wrapping all Agent Gateway API endpoints.
 *
 * Phase 8 stub: all methods throw NotImplementedError.
 * Phase 9 (loop integration) implements the real HTTP calls.
 */
class AgentGatewayClient(
    private val config: GatewayConfig,
    private val httpClient: HttpClient,
) {
    private val logger = LoggerFactory.getLogger(AgentGatewayClient::class.java)

    /** Read agent events from the agent's own stream. */
    suspend fun readAgentEvents(from: Long, limit: Int): Nothing {
        throw NotImplementedError("AgentGatewayClient.readAgentEvents — implemented in Phase 9")
    }

    /** Append events to the agent's own stream. */
    suspend fun appendAgentEvents(events: List<Any>, expectedVersion: Any): Nothing {
        throw NotImplementedError("AgentGatewayClient.appendAgentEvents — implemented in Phase 9")
    }

    /** Read conversation events for conversations this agent is a member of. */
    suspend fun readConversationEvents(convName: String?, from: Long?): Nothing {
        throw NotImplementedError("AgentGatewayClient.readConversationEvents — implemented in Phase 9")
    }

    /** Send a message to a conversation via the gateway. */
    suspend fun sendConversationMessage(convName: String, content: String, sender: String): Nothing {
        throw NotImplementedError("AgentGatewayClient.sendConversationMessage — implemented in Phase 9")
    }

    /** Subscribe to conversation events via SSE. */
    suspend fun subscribeConversationEvents(convName: String?, onEvent: (Any) -> Unit): Nothing {
        throw NotImplementedError("AgentGatewayClient.subscribeConversationEvents — implemented in Phase 9")
    }
}
