package com.bigboote.domain.testing

import com.bigboote.domain.aggregates.AssistantStatus
import com.bigboote.domain.aggregates.LoopStatus
import com.bigboote.domain.events.AgentEvent
import com.bigboote.domain.events.AgentTypeEvent
import com.bigboote.domain.events.DirectMessageEvent
import com.bigboote.domain.events.DocumentEvent
import com.bigboote.domain.events.EffortEvent
import com.bigboote.domain.events.Event
import com.bigboote.domain.events.GroupChannelEvent
import com.bigboote.domain.events.LoopEvent
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * All example test events (except those demonstrating a one-off test case) should be defined here
 * as a "Case".
 */

object Events {
    val now = Clock.System.now()

    val effortCreated = Case(
        EffortEvent.EffortCreated(
            name = "Add OAuth2 support",
            goal = "Implement OAuth2 authorization code flow.",
            collaborators = listOf(
                CollaboratorSpec(
                    name = CollaboratorName.Individual("lead-dev"),
                    type = CollaboratorType.AGENT,
                    agentTypeId = AgentTypeId("lead-eng"),
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

    val effortStarted = Case(
        EffortEvent.EffortStarted(
            occurredAt = now,
        )
    )

    val effortPaused = Case(
        EffortEvent.EffortPaused(
            occurredAt = now,
        )
    )

    val effortResumed = Case(
        EffortEvent.EffortResumed(
            occurredAt = now,
        )
    )

    val effortClosed = Case(
        EffortEvent.EffortClosed(
            occurredAt = now,
        )
    )

    val agentSpawnRequested = Case(
        EffortEvent.AgentSpawnRequested(
            agentId = AgentId("AG-SPAWNTEST000000000000"),
            agentTypeId = AgentTypeId("lead-eng"),
            collaboratorName = CollaboratorName.Individual("lead-dev"),
            gatewayToken = "550e8400-e29b-41d4-a716-446655440000",
            agentToken = "661f9511-f30c-52e5-b827-557766551111",
            requestedAt = now,
        )
    )

    val agentStarted = Case(
        AgentEvent.AgentStarted(
            agentTypeId = AgentTypeId("lead-eng"),
            collaboratorName = CollaboratorName.Individual("lead-dev"),
            supportedGatewayApiVersions = listOf("v1"),
            supportedControlApiVersions = listOf("v1"),
            startedAt = now,
        )
    )

    val agentStopped = Case(
        AgentEvent.AgentStopped(
            occurredAt = now,
        )
    )

    val agentFailed = Case(
        AgentEvent.AgentFailed(
            reason = "Unhandled exception in tool executor: connection refused",
            failedAt = now,
        )
    )

    val agentPaused = Case(
        AgentEvent.AgentPaused(
            occurredAt = now,
        )
    )

    val agentResumed = Case(
        AgentEvent.AgentResumed(
            occurredAt = now,
        )
    )

    val llmRequestSent = Case(
        LoopEvent.LLMRequestSent(
            model = "claude-sonnet-4-6",
            inputTokens = 1540,
            occurredAt = now,
        )
    )

    val llmResponseReceived = Case(
        LoopEvent.LLMResponseReceived(
            outputTokens = 312,
            occurredAt = now,
        )
    )

    val toolInvoked = Case(
        AgentEvent.ToolInvoked(
            toolName = "run_command",
            toolCallId = "toolu_01ABC",
            parameters = JsonObject(mapOf("command" to JsonPrimitive("cat src/auth/oauth.kt"))),
            invokedAt = now,
        )
    )

    val toolResultReceived = Case(
        AgentEvent.ToolResultReceived(
            toolCallId = "toolu_01ABC",
            result = "package com.bigboote.auth ...",
            isError = false,
            receivedAt = now,
        )
    )

    val stepStarted = Case(LoopEvent.StepStarted)

    val stepEnded = Case(
        LoopEvent.StepEnded(
            result = LoopStatus.PENDING
        )
    )

    val assistantTurnSucceeded = Case(
        LoopEvent.AssistantTurnSucceeded(
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
    )

    val assistantTurnSucceededWithNullNewMessage = Case(
        extra = "no new message",
        subject = LoopEvent.AssistantTurnSucceeded(
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
    )

    val assistantTurnFailed = Case(
        LoopEvent.AssistantTurnFailed(
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
    )

    val toolUseRequested = Case(
        LoopEvent.ToolUseRequested(
            content = listOf(Text("What's the weather like?"))
        )
    )

    val channelCreated = Case(
        GroupChannelEvent.ChannelCreated(
            members = listOf(
                CollaboratorName.Individual("lead-dev"),
                CollaboratorName.Individual("reviewer"),
                CollaboratorName.Individual("alice"),
            )
        )
    )

    val membersAdded = Case(
        GroupChannelEvent.MembersAdded(
            members = setOf(CollaboratorName.Individual("bob"))
        )
    )

    val channelMessagePosted = Case(
        GroupChannelEvent.ChannelMessagePosted(
            messageId = MessageId("msg:Xn4wQpL7jTtest"),
            from = CollaboratorName.Individual("alice"),
            to = setOf(
                CollaboratorName.Individual("lead-dev"),
                CollaboratorName.Individual("reviewer")
            ),
            body = "One comment on the token expiry logic."
        )
    )

    val directMessagePosted = Case(
        DirectMessageEvent.DirectMessagePosted(
            messageId = MessageId("msg:Xn4wQpL7jTtest"),
            from = CollaboratorName.Individual("alice"),
            body = "One comment on the token expiry logic."
        )
    )

    val documentCreated = Case(
        DocumentEvent.DocumentCreated(
            documentId = DocumentId("doc:Bn8rYpM3kQtest"),
            name = "oauth2-design.md",
            mimeType = "text/markdown",
            s3Key = "efforts/effort:test123roundtrip/docs/doc:Bn8rYpM3kQtest/oauth2-design.md",
            createdBy = CollaboratorName.Individual("lead-dev"),
            createdAt = now,
        )
    )

    val documentUpdated = Case(
        DocumentEvent.DocumentUpdated(
            documentId = DocumentId("doc:Bn8rYpM3kQtest"),
            s3Key = "efforts/effort:test123roundtrip/docs/doc:Bn8rYpM3kQtest/oauth2-design.md",
            updatedBy = CollaboratorName.Individual("reviewer"),
            updatedAt = now,
        )
    )

    val documentDeleted = Case(
        DocumentEvent.DocumentDeleted(
            documentId = DocumentId("doc:Bn8rYpM3kQtest"),
            deletedBy = CollaboratorName.Individual("lead-dev"),
            deletedAt = now,
        )
    )

    val agentTypeCreated = Case(
        AgentTypeEvent.AgentTypeCreated(
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

    val agentTypeUpdated = Case(
        AgentTypeEvent.AgentTypeUpdated(
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

    val agentTypeUpdatedMinimal = Case(
        extra = "minimal",
        subject = AgentTypeEvent.AgentTypeUpdated(
            updatedAt = now,
        )
    )

    val cases: List<Case> = listOf(
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
        directMessagePosted,
        documentCreated,
        documentUpdated,
        documentDeleted,
        agentTypeCreated,
        agentTypeUpdated,
        agentTypeUpdatedMinimal,
    )

    data class Case(
        override val subject: Event,
        override val extra: String? = null
    ) : TestCaseItem<Event>()
}

