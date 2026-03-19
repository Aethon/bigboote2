package com.bigboote.domain.aggregates

import com.bigboote.domain.events.AgentTypeEvent
import com.bigboote.domain.events.AgentTypeEvent.*
import com.bigboote.domain.values.AgentTypeId
import com.bigboote.domain.values.StreamName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock

class AgentTypeStateTest : StringSpec({

    val now = Clock.System.now()
    val agentTypeId = AgentTypeId.of("lead-eng")
    val streamName = StreamName.AgentType(agentTypeId)

    val createdEvent = EventLogEntryImpl<AgentTypeEvent>(
        streamName = streamName,
        event = AgentTypeCreated(
            name = "Lead Engineer",
            model = "claude-sonnet-4-6",
            systemPrompt = "You are a senior engineer.",
            maxTokens = 8192,
            temperature = 0.7,
            tools = listOf("send_message", "read_document"),
            dockerImage = "bigboote/agent-service:latest",
            spawnStrategy = "docker",
            createdAt = now,
        ),
        context = EventContext(0, 0)
    )

    "apply AgentTypeCreated initializes state" {
        val state = AgentTypeState.start(createdEvent)
        state.name shouldBe "Lead Engineer"
        state.model shouldBe "claude-sonnet-4-6"
        state.maxTokens shouldBe 8192
        state.temperature shouldBe 0.7
        state.tools shouldBe listOf("send_message", "read_document")
    }

    "apply AgentTypeCreated with null optionals uses defaults" {
        val originalEvent = createdEvent.event as AgentTypeCreated
        val newEntry =
            EventLogEntryImpl(streamName, originalEvent.copy(temperature = null, tools = null), EventContext(1, 2))
        val state = AgentTypeState.start(newEntry)
        state.temperature shouldBe 0.0
        state.tools shouldBe emptyList()
    }

    "apply AgentTypeUpdated merges non-null fields" {
        val state = AgentTypeState
            .start(createdEvent)
            .apply(
                EventLogEntryImpl(
                    streamName,
                    AgentTypeUpdated(
                        name = "Senior Engineer",
                        model = "claude-opus-4-6",
                        updatedAt = now,
                    ),
                    EventContext(1, 4)
                )
            )
        state.name shouldBe "Senior Engineer"
        state.model shouldBe "claude-opus-4-6"
        // Unchanged fields preserved
        state.systemPrompt shouldBe "You are a senior engineer."
        state.maxTokens shouldBe 8192
        state.temperature shouldBe 0.7
        state.dockerImage shouldBe "bigboote/agent-service:latest"
    }

    "apply AgentTypeUpdated with all null fields preserves state" {
        val before = AgentTypeState.start(createdEvent)
        val after = before.apply(
            EventLogEntryImpl(streamName, AgentTypeUpdated(updatedAt = now), EventContext(1, 2))
        )
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
        val state = AgentTypeState
            .start(createdEvent)
            .apply(
                EventLogEntryImpl(
                    streamName,
                    AgentTypeUpdated(
                        tools = listOf("deploy"),
                        updatedAt = now,
                    ),
                    EventContext(1, 4)
                )
            )
        state.tools shouldBe listOf("deploy")
    }
})
