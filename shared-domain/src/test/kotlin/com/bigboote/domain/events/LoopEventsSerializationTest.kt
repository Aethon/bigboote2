package com.bigboote.domain.events

import com.bigboote.domain.aggregates.AssistantStatus
import com.bigboote.domain.aggregates.LoopStatus
import com.bigboote.domain.events.LoopEvent.*
import com.bigboote.domain.values.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*

class LoopEventsSerializationTest : StringSpec({

    val json = Json { encodeDefaults = true }
    val now = Clock.System.now()
    val agentId = AgentId("agent:test123")

    "StepStarted round-trip" {
        val event: LoopEvent = StepStarted(agentId = agentId, startedAt = now)
        val encoded = json.encodeToString<LoopEvent>(event)
        val decoded = json.decodeFromString<LoopEvent>(encoded)
        decoded shouldBe event
    }

    "StepEnded round-trip" {
        val event: LoopEvent = StepEnded(
            agentId = agentId,
            result = LoopStatus.PENDING,
            endedAt = now,
        )
        val encoded = json.encodeToString<LoopEvent>(event)
        val decoded = json.decodeFromString<LoopEvent>(encoded)
        decoded shouldBe event
    }

    "AssistantTurnSucceeded round-trip" {
        val event: LoopEvent = AssistantTurnSucceeded(
            agentId = agentId,
            newMessage = buildJsonObject {
                put("role", "user")
                putJsonArray("content") { add(JsonPrimitive("Hello")) }
            },
            response = buildJsonObject {
                put("role", "assistant")
                putJsonArray("content") { add(JsonPrimitive("Hi there")) }
                put("stopReason", "end_turn")
            },
            assistantStatus = AssistantStatus.IDLE,
            satisfiedContentIds = listOf("msg:test1"),
            occurredAt = now,
        )
        val encoded = json.encodeToString<LoopEvent>(event)
        val decoded = json.decodeFromString<LoopEvent>(encoded)
        decoded shouldBe event
    }

    "AssistantTurnSucceeded with null newMessage" {
        val event: LoopEvent = AssistantTurnSucceeded(
            agentId = agentId,
            newMessage = null,
            response = buildJsonObject { put("role", "assistant") },
            assistantStatus = AssistantStatus.PAUSED,
            occurredAt = now,
        )
        val encoded = json.encodeToString<LoopEvent>(event)
        val decoded = json.decodeFromString<LoopEvent>(encoded)
        decoded shouldBe event
    }

    "AssistantTurnFailed round-trip" {
        val event: LoopEvent = AssistantTurnFailed(
            agentId = agentId,
            newMessage = null,
            httpStatusCode = 500,
            httpStatus = "Internal Server Error",
            error = buildJsonObject {
                put("type", "api_error")
                put("message", "Something went wrong")
            },
            occurredAt = now,
        )
        val encoded = json.encodeToString<LoopEvent>(event)
        val decoded = json.decodeFromString<LoopEvent>(encoded)
        decoded shouldBe event
    }

    "ToolUseRequested round-trip" {
        val event: LoopEvent = ToolUseRequested(
            agentId = agentId,
            content = buildJsonArray {
                addJsonObject {
                    put("id", "toolu_01ABC")
                    put("type", "tool_use")
                    put("name", "run_command")
                    putJsonObject("input") { put("command", "ls") }
                }
            },
            requestedAt = now,
        )
        val encoded = json.encodeToString<LoopEvent>(event)
        val decoded = json.decodeFromString<LoopEvent>(encoded)
        decoded shouldBe event
    }

    "ConversationMessageReceived round-trip" {
        val event: LoopEvent = ConversationMessageReceived(
            agentId = agentId,
            messageId = MessageId("msg:test1"),
            convName = CollaboratorName.Channel("review"),
            from = CollaboratorName.Individual("alice"),
            body = "One comment on the token expiry logic.",
            receivedAt = now,
        )
        val encoded = json.encodeToString<LoopEvent>(event)
        val decoded = json.decodeFromString<LoopEvent>(encoded)
        decoded shouldBe event
    }
})
