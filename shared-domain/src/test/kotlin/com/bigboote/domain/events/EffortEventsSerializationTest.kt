package com.bigboote.domain.events

import com.bigboote.domain.events.EffortEvent.*
import com.bigboote.domain.values.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class EffortEventsSerializationTest : StringSpec({

    val json = Json { encodeDefaults = true }
    val now = Clock.System.now()

    "EffortCreated round-trip" {
        val event: EffortEvent = EffortCreated(
            name = "Add OAuth2 support",
            goal = "Implement OAuth2 authorization code flow.",
            collaborators = listOf(
                CollaboratorSpec(
                    name = CollaboratorName.Individual("lead-dev"),
                    type = CollaboratorType.AGENT,
                    agentTypeId = AgentTypeId.of("lead-eng"),
                    isLead = true,
                ),
                CollaboratorSpec(
                    name = CollaboratorName.Individual("alice"),
                    type = CollaboratorType.EXTERNAL,
                ),
            ),
            leadName = CollaboratorName.Individual("lead-dev"),
            createdAt = now,
        )
        val encoded = json.encodeToString<EffortEvent>(event)
        val decoded = json.decodeFromString<EffortEvent>(encoded)
        decoded shouldBe event
    }

    "EffortStarted round-trip" {
        val event: EffortEvent = EffortStarted(
            occurredAt = now,
        )
        val encoded = json.encodeToString<EffortEvent>(event)
        val decoded = json.decodeFromString<EffortEvent>(encoded)
        decoded shouldBe event
    }

    "EffortPaused round-trip" {
        val event: EffortEvent = EffortPaused(
            occurredAt = now,
        )
        val encoded = json.encodeToString<EffortEvent>(event)
        val decoded = json.decodeFromString<EffortEvent>(encoded)
        decoded shouldBe event
    }

    "EffortResumed round-trip" {
        val event: EffortEvent = EffortResumed(
            occurredAt = now,
        )
        val encoded = json.encodeToString<EffortEvent>(event)
        val decoded = json.decodeFromString<EffortEvent>(encoded)
        decoded shouldBe event
    }

    "EffortClosed round-trip" {
        val event: EffortEvent = EffortClosed(
            occurredAt = now,
        )
        val encoded = json.encodeToString<EffortEvent>(event)
        val decoded = json.decodeFromString<EffortEvent>(encoded)
        decoded shouldBe event
    }

    "AgentSpawnRequested round-trip" {
        val event: EffortEvent = AgentSpawnRequested(
            agentId = AgentId("agent:spawn123"),
            agentTypeId = AgentTypeId.of("lead-eng"),
            collaboratorName = CollaboratorName.Individual("lead-dev"),
            gatewayToken = "550e8400-e29b-41d4-a716-446655440000",
            agentToken = "661f9511-f30c-52e5-b827-557766551111",
            requestedAt = now,
        )
        val encoded = json.encodeToString<EffortEvent>(event)
        val decoded = json.decodeFromString<EffortEvent>(encoded)
        decoded shouldBe event
    }
})
