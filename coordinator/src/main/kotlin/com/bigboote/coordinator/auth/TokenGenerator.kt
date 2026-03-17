package com.bigboote.coordinator.auth

import java.util.UUID

/**
 * Generates unique, unguessable tokens for agent authentication.
 *
 * Each spawned agent receives two tokens generated here:
 * - A gateway token delivered as BIGBOOTE_GATEWAY_TOKEN (used by X-Gateway-Token header)
 * - An agent token delivered as BIGBOOTE_AGENT_TOKEN (used by X-Agent-Token header)
 *
 * Both tokens are generated before spawn, stored in AgentSpawnRequested for audit,
 * and provisioned atomically into the container by the SpawnStrategy.
 *
 * See API Design doc Section 2.2 and Architecture doc Section 7.2.
 */
class TokenGenerator {

    /**
     * Generate a new random token as a UUID string.
     * UUIDs are 128-bit random values formatted as hex with hyphens.
     */
    fun generate(): String = UUID.randomUUID().toString()
}
