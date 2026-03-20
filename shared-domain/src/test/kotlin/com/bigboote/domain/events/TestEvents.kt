package com.bigboote.domain.events

import com.bigboote.domain.aggregates.AssistantStatus
import com.bigboote.domain.aggregates.LoopStatus
import com.bigboote.domain.values.AgentId
import com.bigboote.domain.values.AgentTypeId
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.CollaboratorSpec
import com.bigboote.domain.values.CollaboratorType
import com.bigboote.domain.values.DocumentId
import com.bigboote.domain.values.MessageId
import com.xemantic.ai.anthropic.content.Text
import com.xemantic.ai.anthropic.error.ErrorResponse
import com.xemantic.ai.anthropic.error.MessageError
import com.xemantic.ai.anthropic.message.Message
import com.xemantic.ai.anthropic.message.MessageResponse
import com.xemantic.ai.anthropic.message.Role
import com.xemantic.ai.anthropic.message.StopReason
import com.xemantic.ai.anthropic.usage.Usage
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
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

object TestEvents {
    val now = Clock.System.now()

    val effortCreated = EffortEvent.EffortCreated(
        name = "Add OAuth2 support",
        goal = "Implement OAuth2 authorization code flow.",
        collaborators = listOf(
            CollaboratorSpec(
                name = CollaboratorName.Individual("lead-dev"),
                type = CollaboratorType.AGENT,
                agentTypeId = AgentTypeId.Companion.of("lead-eng"),
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

    val effortStarted = EffortEvent.EffortStarted(
        occurredAt = now,
    )

    val effortPaused = EffortEvent.EffortPaused(
        occurredAt = now,
    )

    val effortResumed = EffortEvent.EffortResumed(
        occurredAt = now,
    )

    val effortClosed = EffortEvent.EffortClosed(
        occurredAt = now,
    )

    val agentSpawnRequested = EffortEvent.AgentSpawnRequested(
        agentId = AgentId("agent:spawn123test"),
        agentTypeId = AgentTypeId.Companion.of("lead-eng"),
        collaboratorName = CollaboratorName.Individual("lead-dev"),
        gatewayToken = "550e8400-e29b-41d4-a716-446655440000",
        agentToken = "661f9511-f30c-52e5-b827-557766551111",
        requestedAt = now,
    )

    val agentStarted = AgentEvent.AgentStarted(
        agentTypeId = AgentTypeId.Companion.of("lead-eng"),
        collaboratorName = CollaboratorName.Individual("lead-dev"),
        supportedGatewayApiVersions = listOf("v1"),
        supportedControlApiVersions = listOf("v1"),
        startedAt = now,
    )

    val agentStopped = AgentEvent.AgentStopped(
        occurredAt = now,
    )

    val agentFailed = AgentEvent.AgentFailed(
        reason = "Unhandled exception in tool executor: connection refused",
        failedAt = now,
    )

    val agentPaused = AgentEvent.AgentPaused(
        occurredAt = now,
    )

    val agentResumed = AgentEvent.AgentResumed(
        occurredAt = now,
    )

    val llmRequestSent = LoopEvent.LLMRequestSent(
        model = "claude-sonnet-4-6",
        inputTokens = 1540,
        occurredAt = now,
    )

    val llmResponseReceived = LoopEvent.LLMResponseReceived(
        outputTokens = 312,
        occurredAt = now,
    )

    val toolInvoked = AgentEvent.ToolInvoked(
        toolName = "run_command",
        toolCallId = "toolu_01ABC",
        parameters = JsonObject(mapOf("command" to JsonPrimitive("cat src/auth/oauth.kt"))),
        invokedAt = now,
    )

    val toolResultReceived = AgentEvent.ToolResultReceived(
        toolCallId = "toolu_01ABC",
        result = "package com.bigboote.auth ...",
        isError = false,
        receivedAt = now,
    )

    val stepStarted = LoopEvent.StepStarted

    val stepEnded = LoopEvent.StepEnded(
        result = LoopStatus.PENDING
    )

    val assistantTurnSucceeded = LoopEvent.AssistantTurnSucceeded(
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
        assistantStatus = AssistantStatus.TOOL_USE,
        lastSentMessageId = MessageId("msg:Xn4wQpL7jTtest")
    )

    val assistantTurnSucceededWithNullNewMessage = LoopEvent.AssistantTurnSucceeded(
        newMessage = null,
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
        lastSentMessageId = null
    )

    val assistantTurnFailed = LoopEvent.AssistantTurnFailed(
        newMessage = Message(
            role = Role.USER,
            content = listOf(Text("Hello"))
        ),
        httpStatusCode = 529,
        httpStatus = "Overloaded",
        error = ErrorResponse(
            MessageError(
                type = "api_error",
                message = "Something went wrong"
            )
        )
    )

    val toolUseRequested = LoopEvent.ToolUseRequested(
        content = listOf(Text("What's the weather like?"))
    )

    val channelCreated = GroupChannelEvent.ChannelCreated(
        members = listOf(
            CollaboratorName.Individual("lead-dev"),
            CollaboratorName.Individual("reviewer"),
            CollaboratorName.Individual("alice"),
        )
    )

    val membersAdded = GroupChannelEvent.MembersAdded(
        members = setOf(CollaboratorName.Individual("bob"))
    )

    val channelMessagePosted = GroupChannelEvent.ChannelMessagePosted(
        messageId = MessageId("msg:Xn4wQpL7jTtest"),
        from = CollaboratorName.Individual("alice"),
        to = setOf(
            CollaboratorName.Individual("lead-dev"),
            CollaboratorName.Individual("reviewer")
        ),
        body = "One comment on the token expiry logic."
    )

    val documentCreated = DocumentEvent.DocumentCreated(
        documentId = DocumentId("doc:Bn8rYpM3kQtest"),
        name = "oauth2-design.md",
        mimeType = "text/markdown",
        s3Key = "efforts/effort:test123roundtrip/docs/doc:Bn8rYpM3kQtest/oauth2-design.md",
        createdBy = CollaboratorName.Individual("lead-dev"),
        createdAt = now,
    )

    val documentUpdated = DocumentEvent.DocumentUpdated(
        documentId = DocumentId("doc:Bn8rYpM3kQtest"),
        s3Key = "efforts/effort:test123roundtrip/docs/doc:Bn8rYpM3kQtest/oauth2-design.md",
        updatedBy = CollaboratorName.Individual("reviewer"),
        updatedAt = now,
    )

    val documentDeleted = DocumentEvent.DocumentDeleted(
        documentId = DocumentId("doc:Bn8rYpM3kQtest"),
        deletedBy = CollaboratorName.Individual("lead-dev"),
        deletedAt = now,
    )

    val agentTypeCreated = AgentTypeEvent.AgentTypeCreated(
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

    val agentTypeUpdated = AgentTypeEvent.AgentTypeUpdated(
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

    val agentTypeUpdatedMinimal = AgentTypeEvent.AgentTypeUpdated(
        updatedAt = now,
    )

    val Examples: List<EventCase> = listOf(
        effortCreated,
        effortStarted,
        effortPaused,
        effortResumed,
        effortClosed,
        agentSpawnRequested,
        agentStarted,
        agentStopped,
        agentFailed,
        agentPaused,
        agentResumed,
        llmRequestSent,
        llmResponseReceived,
        toolInvoked,
        toolResultReceived,
        stepStarted,
        stepEnded,
        assistantTurnSucceeded,
        assistantTurnSucceededWithNullNewMessage,
        assistantTurnFailed,
        toolUseRequested,
        channelCreated,
        membersAdded,
        channelMessagePosted,
        documentCreated,
        documentUpdated,
        documentDeleted,
        agentTypeCreated,
        agentTypeUpdated,
        agentTypeUpdatedMinimal,
    )
}

data class EventCase(
    val name: String? = null,
    val event: Event
)