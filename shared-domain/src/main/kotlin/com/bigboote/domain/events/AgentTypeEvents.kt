package com.bigboote.domain.events

import com.bigboote.domain.values.AgentTypeId
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface AgentTypeEvent {
    val agentTypeId: AgentTypeId

    @Serializable
    @SerialName("AgentTypeCreated")
    data class AgentTypeCreated(
        override val agentTypeId: AgentTypeId,
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
        override val agentTypeId: AgentTypeId,
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
