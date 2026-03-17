package com.bigboote.domain.events

import com.bigboote.domain.events.AgentTypeEvent.*
import com.bigboote.domain.values.AgentTypeId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class AgentTypeEventsSerializationTest : StringSpec({

    val json = Json { encodeDefaults = true }
    val now = Clock.System.now()

    "AgentTypeCreated round-trip with all fields" {
        val event: AgentTypeEvent = AgentTypeCreated(
            agentTypeId = AgentTypeId.of("lead-eng"),
            name = "Lead Engineer",
            model = "claude-sonnet-4-6",
            systemPrompt = "You are a senior engineer.",
            maxTokens = 8192,
            temperature = 0.7,
            tools = listOf("send_message", "read_document", "write_document"),
            dockerImage = "bigboote/agent-service:latest",
            spawnStrategy = "docker",
            createdAt = now,
        )
        val encoded = json.encodeToString<AgentTypeEvent>(event)
        val decoded = json.decodeFromString<AgentTypeEvent>(encoded)
        decoded shouldBe event
    }

    "AgentTypeCreated round-trip with null optionals" {
        val event: AgentTypeEvent = AgentTypeCreated(
            agentTypeId = AgentTypeId.of("reviewer"),
            name = "Reviewer",
            model = "claude-sonnet-4-6",
            systemPrompt = "You are a code reviewer.",
            maxTokens = 4096,
            temperature = null,
            tools = null,
            dockerImage = "bigboote/agent-service:latest",
            spawnStrategy = "docker",
            createdAt = now,
        )
        val encoded = json.encodeToString<AgentTypeEvent>(event)
        val decoded = json.decodeFromString<AgentTypeEvent>(encoded)
        decoded shouldBe event
    }

    "AgentTypeUpdated round-trip with partial fields" {
        val event: AgentTypeEvent = AgentTypeUpdated(
            agentTypeId = AgentTypeId.of("lead-eng"),
            name = "Senior Engineer",
            model = "claude-opus-4-6",
            updatedAt = now,
        )
        val encoded = json.encodeToString<AgentTypeEvent>(event)
        val decoded = json.decodeFromString<AgentTypeEvent>(encoded)
        decoded shouldBe event
    }

    "AgentTypeUpdated round-trip with all fields" {
        val event: AgentTypeEvent = AgentTypeUpdated(
            agentTypeId = AgentTypeId.of("lead-eng"),
            name = "Senior Engineer",
            model = "claude-opus-4-6",
            systemPrompt = "Updated prompt.",
            maxTokens = 16384,
            temperature = 0.5,
            tools = listOf("send_message", "deploy"),
            dockerImage = "bigboote/agent-service:v2",
            spawnStrategy = "fly",
            updatedAt = now,
        )
        val encoded = json.encodeToString<AgentTypeEvent>(event)
        val decoded = json.decodeFromString<AgentTypeEvent>(encoded)
        decoded shouldBe event
    }
})
