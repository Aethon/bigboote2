package com.bigboote.domain.aggregates

import com.bigboote.domain.events.AgentTypeEvent
import com.bigboote.domain.events.AgentTypeEvent.*
import com.bigboote.domain.events.EventLogEntry
import com.bigboote.domain.events.NoContextStreamState
import com.bigboote.domain.events.StreamStateStarter
import com.bigboote.domain.values.AgentTypeId
import kotlinx.datetime.Instant

data class AgentTypeState(
    val name: String,
    val model: String,
    val systemPrompt: String,
    val maxTokens: Int,
    val temperature: Double,
    val tools: List<String>,
    val dockerImage: String,
    val spawnStrategy: String,
    val createdAt: Instant,
) : NoContextStreamState<AgentTypeEvent, AgentTypeState>() {
    override fun apply(event: AgentTypeEvent): AgentTypeState = when (event) {
        is AgentTypeCreated -> throw IllegalArgumentException("Already created")

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

    companion object : StreamStateStarter<AgentTypeEvent, AgentTypeState> {
        override fun start(entry: EventLogEntry<AgentTypeEvent>): AgentTypeState {
            val event = entry.event as? AgentTypeCreated
                ?: throw IllegalArgumentException("Must start with AgentTypeCreated event")
            return AgentTypeState(
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
        }
    }
}

