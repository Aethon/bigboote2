package com.bigboote.domain.events

import com.bigboote.domain.events.AgentEvent.*
import com.bigboote.domain.values.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AgentEventsSerializationTest : StringSpec({

    val json = Json { encodeDefaults = true }
    val now = Clock.System.now()

    "AgentStarted round-trip" {
        val event: AgentEvent = AgentStarted(
            agentTypeId = AgentTypeId.of("lead-eng"),
            collaboratorName = CollaboratorName.Individual("lead-dev"),
            supportedGatewayApiVersions = listOf("v1"),
            supportedControlApiVersions = listOf("v1"),
            startedAt = now,
        )
        val encoded = json.encodeToString<AgentEvent>(event)
        val decoded = json.decodeFromString<AgentEvent>(encoded)
        decoded shouldBe event
    }

    "AgentStopped round-trip" {
        val event: AgentEvent = AgentStopped(occurredAt = now)
        val encoded = json.encodeToString<AgentEvent>(event)
        val decoded = json.decodeFromString<AgentEvent>(encoded)
        decoded shouldBe event
    }

    "AgentFailed round-trip" {
        val event: AgentEvent = AgentFailed(
            reason = "Connection refused",
            failedAt = now,
        )
        val encoded = json.encodeToString<AgentEvent>(event)
        val decoded = json.decodeFromString<AgentEvent>(encoded)
        decoded shouldBe event
    }

    "AgentPaused round-trip" {
        val event: AgentEvent = AgentPaused(occurredAt = now)
        val encoded = json.encodeToString<AgentEvent>(event)
        val decoded = json.decodeFromString<AgentEvent>(encoded)
        decoded shouldBe event
    }

    "AgentResumed round-trip" {
        val event: AgentEvent = AgentResumed(occurredAt = now)
        val encoded = json.encodeToString<AgentEvent>(event)
        val decoded = json.decodeFromString<AgentEvent>(encoded)
        decoded shouldBe event
    }

    "ToolInvoked round-trip" {
        val event: AgentEvent = ToolInvoked(
            toolName = "run_command",
            toolCallId = "toolu_01ABC",
            parameters = buildJsonObject { put("command", "cat src/auth/oauth.kt") },
            invokedAt = now,
        )
        val encoded = json.encodeToString<AgentEvent>(event)
        val decoded = json.decodeFromString<AgentEvent>(encoded)
        decoded shouldBe event
    }

    "ToolResultReceived round-trip" {
        val event: AgentEvent = ToolResultReceived(
            toolCallId = "toolu_01ABC",
            result = "file contents here",
            isError = false,
            receivedAt = now,
        )
        val encoded = json.encodeToString<AgentEvent>(event)
        val decoded = json.decodeFromString<AgentEvent>(encoded)
        decoded shouldBe event
    }
})
