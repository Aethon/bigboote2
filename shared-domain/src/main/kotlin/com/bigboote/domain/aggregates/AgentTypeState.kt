package com.bigboote.domain.aggregates

import com.bigboote.domain.events.AgentTypeEvent
import com.bigboote.domain.events.AgentTypeEvent.*
import com.bigboote.domain.values.AgentTypeId
import kotlinx.datetime.Instant

data class AgentTypeState(
    val agentTypeId: AgentTypeId,
    val name: String,
    val model: String,
    val systemPrompt: String,
    val maxTokens: Int,
    val temperature: Double,
    val tools: List<String>,
    val dockerImage: String,
    val spawnStrategy: String,
    val createdAt: Instant,
) {
    fun apply(event: AgentTypeEvent): AgentTypeState = when (event) {
        is AgentTypeCreated -> AgentTypeState(
            agentTypeId = event.agentTypeId,
            name = event.name,
            model = event.model,
            systemPrompt = event.systemPrompt,
            maxTokens = event.maxTokens,
            temperature = event.temperature ?: 0.0,
            tools = event.tools ?: emptyList(),
            dockerImage = event.dockerImage,
            spawnStrategy = event.spawnStrategy,
            createdAt = event.createdAt,
        )
        is AgentTypeUpdated -> copy(
            name = event.name ?: name,
            model = event.model ?: model,
            systemPrompt = event.systemPrompt ?: systemPrompt,
            maxTokens = event.maxTokens ?: maxTokens,
            temperature = event.temperature ?: temperature,
            tools = event.tools ?: tools,
            dockerImage = event.dockerImage ?: dockerImage,
            spawnStrategy = event.spawnStrategy ?: spawnStrategy,
        )
    }

    companion object {
        val EMPTY = AgentTypeState(
            agentTypeId = AgentTypeId.of("empty"),
            name = "",
            model = "",
            systemPrompt = "",
            maxTokens = 0,
            temperature = 0.0,
            tools = emptyList(),
            dockerImage = "",
            spawnStrategy = "",
            createdAt = Instant.fromEpochMilliseconds(0),
        )
    }
}
