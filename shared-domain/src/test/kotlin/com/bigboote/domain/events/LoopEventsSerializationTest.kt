package com.bigboote.domain.events

import com.bigboote.domain.aggregates.AssistantStatus
import com.bigboote.domain.aggregates.LoopStatus
import com.bigboote.domain.events.LoopEvent.*
import com.bigboote.domain.values.*
import com.xemantic.ai.anthropic.Response
import com.xemantic.ai.anthropic.content.Text
import com.xemantic.ai.anthropic.error.ErrorResponse
import com.xemantic.ai.anthropic.error.MessageError
import com.xemantic.ai.anthropic.message.Message
import com.xemantic.ai.anthropic.message.MessageResponse
import com.xemantic.ai.anthropic.message.Role
import com.xemantic.ai.anthropic.message.StopReason
import com.xemantic.ai.anthropic.usage.Usage
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*

class LoopEventsSerializationTest : StringSpec({

    val json = Json { encodeDefaults = true }
    val now = Clock.System.now()

    "StepStarted round-trip" {
        val event: LoopEvent = StepStarted(startedAt = now)
        val encoded = json.encodeToString<LoopEvent>(event)
        val decoded = json.decodeFromString<LoopEvent>(encoded)
        decoded shouldBe event
    }

    "StepEnded round-trip" {
        val event: LoopEvent = StepEnded(
            result = LoopStatus.PENDING,
            endedAt = now,
        )
        val encoded = json.encodeToString<LoopEvent>(event)
        val decoded = json.decodeFromString<LoopEvent>(encoded)
        decoded shouldBe event
    }

    "AssistantTurnSucceeded round-trip" {
        val event: LoopEvent = AssistantTurnSucceeded(
            newMessage = Message(
                role = Role.USER,
                content = listOf(Text("Hello"))
            ),
            response = MessageResponse(
                role = Role.ASSISTANT,
                content = listOf(Text("Hi there")),
                stopReason = StopReason.END_TURN,
                model = "claude-3-sonnet-20240229",
                id = "msg_0123456789",
                stopSequence = null,
                usage = Usage {
                    inputTokens = 100
                    outputTokens = 200
                }
            ),
            assistantStatus = AssistantStatus.IDLE,
            satisfiedContentIds = setOf(MessageId("msg:test1"))
        )
        val encoded = json.encodeToString<LoopEvent>(event)
        val decoded = json.decodeFromString<LoopEvent>(encoded)
        decoded shouldBe event
    }

    "AssistantTurnSucceeded with null newMessage" {
        val event: LoopEvent = AssistantTurnSucceeded(
            newMessage = null,
            response = MessageResponse(
                role = Role.ASSISTANT,
                content = listOf(Text("Uh, what?")),
                stopReason = StopReason.END_TURN,
                model = "claude-3-sonnet-20240229",
                id = "msg_0123456789",
                stopSequence = null,
                usage = Usage {
                    inputTokens = 100
                    outputTokens = 200
                }
            ),
            assistantStatus = AssistantStatus.PAUSED
        )
        val encoded = json.encodeToString<LoopEvent>(event)
        val decoded = json.decodeFromString<LoopEvent>(encoded)
        decoded shouldBe event
    }

    "AssistantTurnFailed round-trip" {
        val event: LoopEvent = AssistantTurnFailed(
            newMessage = null,
            httpStatusCode = 500,
            httpStatus = "Internal Server Error",
            error = ErrorResponse(
                MessageError(
                    type = "api_error",
                    message = "Something went wrong"
                )
            )
        )
        val encoded = json.encodeToString<LoopEvent>(event)
        val decoded = json.decodeFromString<LoopEvent>(encoded)
        decoded shouldBe event
    }

    "ToolUseRequested round-trip" {
        val event: LoopEvent = ToolUseRequested(
            content = listOf(Text("What's the weather like?")) // TODO: real tool request
        )
        val encoded = json.encodeToString<LoopEvent>(event)
        val decoded = json.decodeFromString<LoopEvent>(encoded)
        decoded shouldBe event
    }

//    "ConversationMessageReceived round-trip" {
//        val event: LoopEvent = ConversationMessageReceived(
//            messageId = MessageId("msg:test1"),
//            convName = CollaboratorName.Channel("review"),
//            from = CollaboratorName.Individual("alice"),
//            body = "One comment on the token expiry logic.",
//            receivedAt = now,
//        )
//        val encoded = json.encodeToString<LoopEvent>(event)
//        val decoded = json.decodeFromString<LoopEvent>(encoded)
//        decoded shouldBe event
//    }
})
