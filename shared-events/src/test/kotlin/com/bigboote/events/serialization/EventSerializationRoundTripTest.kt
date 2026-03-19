package com.bigboote.events.serialization

import com.bigboote.domain.aggregates.AssistantStatus
import com.bigboote.domain.aggregates.LoopStatus
import com.bigboote.domain.events.AgentEvent.*
import com.bigboote.domain.events.AgentTypeEvent.*
import com.bigboote.domain.events.ConversationEvent.*
import com.bigboote.domain.events.DocumentEvent.*
import com.bigboote.domain.events.EffortEvent.*
import com.bigboote.domain.events.LoopEvent.*
import com.bigboote.domain.values.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Round-trip serialization tests for all event types through EventRegistry.
 *
 * For each event:
 * 1. Construct the event with realistic test data
 * 2. Look up the event type name via EventRegistry.eventTypeOf()
 * 3. Serialize to JSON using the registered KSerializer
 * 4. Deserialize from JSON using the same KSerializer
 * 5. Assert equality with the original event
 *
 * This verifies the full serialize → deserialize path that EventSerializer
 * and EventDeserializer use (without requiring a running KurrentDB instance).
 */
class EventSerializationRoundTripTest : StringSpec({

    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    val now = Clock.System.now()

    fun roundTrip(event: Any) {
        val eventType = EventRegistry.eventTypeOf(event)
        eventType.shouldNotBeEmpty()

        @Suppress("UNCHECKED_CAST")
        val serializer = EventRegistry.serializerFor(eventType)!! as kotlinx.serialization.KSerializer<Any>
        val encoded = json.encodeToString(serializer, event)
        val decoded = json.decodeFromString(serializer, encoded)
        decoded shouldBe event
    }

    // --- Effort Events ---

    "EffortCreated round-trip through EventRegistry" {
        roundTrip(
            EffortCreated(
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
        )
    }

    "EffortStarted round-trip through EventRegistry" {
        roundTrip(
            EffortStarted(
                occurredAt = now,
            )
        )
    }

    "EffortPaused round-trip through EventRegistry" {
        roundTrip(
            EffortPaused(
                occurredAt = now,
            )
        )
    }

    "EffortResumed round-trip through EventRegistry" {
        roundTrip(
            EffortResumed(
                occurredAt = now,
            )
        )
    }

    "EffortClosed round-trip through EventRegistry" {
        roundTrip(
            EffortClosed(
                occurredAt = now,
            )
        )
    }

    "AgentSpawnRequested round-trip through EventRegistry" {
        roundTrip(
            AgentSpawnRequested(
                agentId = AgentId("agent:spawn123test"),
                agentTypeId = AgentTypeId.of("lead-eng"),
                collaboratorName = CollaboratorName.Individual("lead-dev"),
                gatewayToken = "550e8400-e29b-41d4-a716-446655440000",
                agentToken = "661f9511-f30c-52e5-b827-557766551111",
                requestedAt = now,
            )
        )
    }

    // --- Agent Events ---

    "AgentStarted round-trip through EventRegistry" {
        roundTrip(
            AgentStarted(
                agentTypeId = AgentTypeId.of("lead-eng"),
                collaboratorName = CollaboratorName.Individual("lead-dev"),
                supportedGatewayApiVersions = listOf("v1"),
                supportedControlApiVersions = listOf("v1"),
                startedAt = now,
            )
        )
    }

    "AgentStopped round-trip through EventRegistry" {
        roundTrip(
            AgentStopped(
                occurredAt = now,
            )
        )
    }

    "AgentFailed round-trip through EventRegistry" {
        roundTrip(
            AgentFailed(
                reason = "Unhandled exception in tool executor: connection refused",
                failedAt = now,
            )
        )
    }

    "AgentPaused round-trip through EventRegistry" {
        roundTrip(
            AgentPaused(
                occurredAt = now,
            )
        )
    }

    "AgentResumed round-trip through EventRegistry" {
        roundTrip(
            AgentResumed(
                occurredAt = now,
            )
        )
    }

    "LLMRequestSent round-trip through EventRegistry" {
        roundTrip(
            LLMRequestSent(
                model = "claude-sonnet-4-6",
                inputTokens = 1540,
                occurredAt = now,
            )
        )
    }

    "LLMResponseReceived round-trip through EventRegistry" {
        roundTrip(
            LLMResponseReceived(
                outputTokens = 312,
                occurredAt = now,
            )
        )
    }

    "ToolInvoked round-trip through EventRegistry" {
        roundTrip(
            ToolInvoked(
                toolName = "run_command",
                toolCallId = "toolu_01ABC",
                parameters = JsonObject(mapOf("command" to JsonPrimitive("cat src/auth/oauth.kt"))),
                invokedAt = now,
            )
        )
    }

    "ToolResultReceived round-trip through EventRegistry" {
        roundTrip(
            ToolResultReceived(
                toolCallId = "toolu_01ABC",
                result = "package com.bigboote.auth ...",
                isError = false,
                receivedAt = now,
            )
        )
    }

    // --- Loop Events ---

    "StepStarted round-trip through EventRegistry" {
        roundTrip(
            StepStarted(
                startedAt = now,
            )
        )
    }

    "StepEnded round-trip through EventRegistry" {
        roundTrip(
            StepEnded(
                result = LoopStatus.PENDING,
                endedAt = now,
            )
        )
    }

    "AssistantTurnSucceeded round-trip through EventRegistry" {
        roundTrip(
            AssistantTurnSucceeded(
                newMessage = JsonObject(mapOf("role" to JsonPrimitive("user"))),
                response = JsonObject(mapOf("role" to JsonPrimitive("assistant"))),
                assistantStatus = AssistantStatus.TOOL_USE,
                satisfiedContentIds = listOf("msg:Xn4wQpL7jTtest"),
                occurredAt = now,
            )
        )
    }

    "AssistantTurnSucceeded with null newMessage round-trip" {
        roundTrip(
            AssistantTurnSucceeded(
                newMessage = null,
                response = JsonObject(mapOf("role" to JsonPrimitive("assistant"))),
                assistantStatus = AssistantStatus.IDLE,
                satisfiedContentIds = emptyList(),
                occurredAt = now,
            )
        )
    }

    "AssistantTurnFailed round-trip through EventRegistry" {
        roundTrip(
            AssistantTurnFailed(
                newMessage = JsonObject(mapOf("role" to JsonPrimitive("user"))),
                httpStatusCode = 529,
                httpStatus = "Overloaded",
                error = JsonObject(mapOf("type" to JsonPrimitive("overloaded_error"))),
                occurredAt = now,
            )
        )
    }

    "ToolUseRequested round-trip through EventRegistry" {
        roundTrip(
            ToolUseRequested(
                content = JsonObject(
                    mapOf(
                        "id" to JsonPrimitive("toolu_01ABC"),
                        "type" to JsonPrimitive("tool_use"),
                        "name" to JsonPrimitive("run_command"),
                    )
                ),
                requestedAt = now,
            )
        )
    }

    "ConversationMessageReceived round-trip through EventRegistry" {
        roundTrip(
            ConversationMessageReceived(
                messageId = MessageId("msg:Xn4wQpL7jTtest"),
                convName = CollaboratorName.Channel("review"),
                from = CollaboratorName.Individual("alice"),
                body = "One comment on the token expiry logic.",
                receivedAt = now,
            )
        )
    }

    // --- Conversation Events ---

    "ConversationCreated round-trip through EventRegistry" {
        roundTrip(
            ConversationCreated(
                convName = CollaboratorName.Channel("review"),
                members = listOf(
                    CollaboratorName.Individual("lead-dev"),
                    CollaboratorName.Individual("reviewer"),
                    CollaboratorName.Individual("alice"),
                ),
                createdAt = now,
            )
        )
    }

    "MemberAdded round-trip through EventRegistry" {
        roundTrip(
            MemberAdded(
                member = CollaboratorName.Individual("bob"),
                addedAt = now,
            )
        )
    }

    "MessagePosted round-trip through EventRegistry" {
        roundTrip(
            MessagePosted(
                messageId = MessageId("msg:Xn4wQpL7jTtest"),
                from = CollaboratorName.Individual("alice"),
                body = "One comment on the token expiry logic.",
                postedAt = now,
            )
        )
    }

    // --- Document Events ---

    "DocumentCreated round-trip through EventRegistry" {
        roundTrip(
            DocumentCreated(
                documentId = DocumentId("doc:Bn8rYpM3kQtest"),
                name = "oauth2-design.md",
                mimeType = "text/markdown",
                s3Key = "efforts/effort:test123roundtrip/docs/doc:Bn8rYpM3kQtest/oauth2-design.md",
                createdBy = CollaboratorName.Individual("lead-dev"),
                createdAt = now,
            )
        )
    }

    "DocumentUpdated round-trip through EventRegistry" {
        roundTrip(
            DocumentUpdated(
                documentId = DocumentId("doc:Bn8rYpM3kQtest"),
                s3Key = "efforts/effort:test123roundtrip/docs/doc:Bn8rYpM3kQtest/oauth2-design.md",
                updatedBy = CollaboratorName.Individual("reviewer"),
                updatedAt = now,
            )
        )
    }

    "DocumentDeleted round-trip through EventRegistry" {
        roundTrip(
            DocumentDeleted(
                documentId = DocumentId("doc:Bn8rYpM3kQtest"),
                deletedBy = CollaboratorName.Individual("lead-dev"),
                deletedAt = now,
            )
        )
    }

    // --- AgentType Events ---

    "AgentTypeCreated round-trip through EventRegistry" {
        roundTrip(
            AgentTypeCreated(
                name = "Lead Engineer",
                model = "claude-sonnet-4-6",
                systemPrompt = "You are a senior engineer leading a focused development effort.",
                maxTokens = 8192,
                temperature = 0.7,
                tools = listOf("send_message", "read_document", "write_document", "run_command"),
                dockerImage = "bigboote/agent-service:latest",
                spawnStrategy = "docker",
                createdAt = now,
            )
        )
    }

    "AgentTypeUpdated round-trip through EventRegistry" {
        roundTrip(
            AgentTypeUpdated(
                name = "Lead Engineer v2",
                model = null,
                systemPrompt = null,
                maxTokens = null,
                temperature = 0.5,
                tools = null,
                dockerImage = null,
                spawnStrategy = null,
                updatedAt = now,
            )
        )
    }

    "AgentTypeUpdated with all fields null except required round-trip" {
        roundTrip(
            AgentTypeUpdated(
                updatedAt = now,
            )
        )
    }
})
