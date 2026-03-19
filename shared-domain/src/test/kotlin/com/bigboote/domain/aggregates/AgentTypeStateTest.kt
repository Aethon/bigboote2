package com.bigboote.domain.aggregates

import com.bigboote.domain.events.AgentTypeEvent.*
import com.bigboote.domain.values.AgentTypeId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock

class AgentTypeStateTest : StringSpec({

    val now = Clock.System.now()
    val agentTypeId = AgentTypeId.of("lead-eng")

    val createdEvent = AgentTypeCreated(
        name = "Lead Engineer",
        model = "claude-sonnet-4-6",
        systemPrompt = "You are a senior engineer.",
        maxTokens = 8192,
        temperature = 0.7,
        tools = listOf("send_message", "read_document"),
        dockerImage = "bigboote/agent-service:latest",
        spawnStrategy = "docker",
        createdAt = now,
    )

    "apply AgentTypeCreated initializes state" {
        val state = AgentTypeState.EMPTY.apply(createdEvent)
        state.name shouldBe "Lead Engineer"
        state.model shouldBe "claude-sonnet-4-6"
        state.maxTokens shouldBe 8192
        state.temperature shouldBe 0.7
        state.tools shouldBe listOf("send_message", "read_document")
    }

    "apply AgentTypeCreated with null optionals uses defaults" {
        val event = createdEvent.copy(temperature = null, tools = null)
        val state = AgentTypeState.EMPTY.apply(event)
        state.temperature shouldBe 0.0
        state.tools shouldBe emptyList()
    }

    "apply AgentTypeUpdated merges non-null fields" {
        val state = AgentTypeState.EMPTY
            .apply(createdEvent)
            .apply(AgentTypeUpdated(
                name = "Senior Engineer",
                model = "claude-opus-4-6",
                updatedAt = now,
            ))
        state.name shouldBe "Senior Engineer"
        state.model shouldBe "claude-opus-4-6"
        // Unchanged fields preserved
        state.systemPrompt shouldBe "You are a senior engineer."
        state.maxTokens shouldBe 8192
        state.temperature shouldBe 0.7
        state.dockerImage shouldBe "bigboote/agent-service:latest"
    }

    "apply AgentTypeUpdated with all null fields preserves state" {
        val before = AgentTypeState.EMPTY.apply(createdEvent)
        val after = before.apply(AgentTypeUpdated(updatedAt = now))
        after.name shouldBe before.name
        after.model shouldBe before.model
        after.systemPrompt shouldBe before.systemPrompt
        after.maxTokens shouldBe before.maxTokens
        after.temperature shouldBe before.temperature
        after.tools shouldBe before.tools
        after.dockerImage shouldBe before.dockerImage
        after.spawnStrategy shouldBe before.spawnStrategy
    }

    "apply AgentTypeUpdated replaces tools list entirely" {
        val state = AgentTypeState.EMPTY
            .apply(createdEvent)
            .apply(AgentTypeUpdated(
                tools = listOf("deploy"),
                updatedAt = now,
            ))
        state.tools shouldBe listOf("deploy")
    }
})
