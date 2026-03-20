package com.bigboote.domain.events

import com.bigboote.domain.values.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Events for the AgentType configuration stream: `/agenttype:{id}`
 *
 * [AgentTypeId] is inherent to [StreamName.AgentType] and is no longer duplicated in
 * event payloads. Retrieve it via [StreamName.AgentType.id] from
 * [com.bigboote.events.eventstore.EventEnvelope.streamName].
 *
 * See Architecture doc Change Document v1.0 Section 5.7.
 */
@Serializable
sealed interface AgentTypeEvent : Event {

    @Serializable
    @SerialName("AgentTypeCreated")
    data class AgentTypeCreated(
        val name: String,
        val model: String,
        val systemPrompt: String,
        val maxTokens: Int,
        val temperature: Double? = null,
        val tools: List<String>? = null,
        val dockerImage: String,
        val spawnStrategy: String,
        val createdAt: Instant,
    ) : AgentTypeEvent

    @Serializable
    @SerialName("AgentTypeUpdated")
    data class AgentTypeUpdated(
        val name: String? = null,
        val model: String? = null,
        val systemPrompt: String? = null,
        val maxTokens: Int? = null,
        val temperature: Double? = null,
        val tools: List<String>? = null,
        val dockerImage: String? = null,
        val spawnStrategy: String? = null,
        val updatedAt: Instant,
    ) : AgentTypeEvent
}

/**
 * Safely cast an untyped [StreamName] to [StreamName.AgentType].
 * Use this in [EventStore.subscribeToAll] handlers after a type-check on `envelope.data`.
 */
fun StreamName<*>.asAgentTypeStream(): StreamName.AgentType =
    this as? StreamName.AgentType
        ?: error("Expected StreamName.AgentType but got ${this::class.simpleName} for path '$path'")
