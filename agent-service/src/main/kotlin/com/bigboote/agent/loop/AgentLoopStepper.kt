package org.aethon.agentrunner

import com.bigboote.agent.loop.AgentConversationState
import com.bigboote.domain.aggregates.AssistantStatus
import com.xemantic.ai.anthropic.ClaudeApiClient
import com.xemantic.ai.anthropic.content.Content
import com.xemantic.ai.anthropic.content.Document
import com.xemantic.ai.anthropic.content.Image
import com.xemantic.ai.anthropic.content.Text
import com.xemantic.ai.anthropic.message.Message
import com.xemantic.ai.anthropic.message.Role
import com.xemantic.ai.anthropic.message.StopReason
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.bigboote.domain.aggregates.LoopState
import com.bigboote.domain.aggregates.LoopStatus
import com.bigboote.domain.aggregates.PendingCollaborationMessage
import com.bigboote.domain.events.LoopEvent
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.MessageId
import org.jetbrains.annotations.VisibleForTesting
import kotlin.time.Clock


class AgentLoopStepper(
    private val agent: CollaboratorName.Individual,
    private val gateway: AgentGateway,
    private val assistant: ClaudeApiClient,
    private val config: AgentLoopConfig,
    private val clock: Clock = Clock.System
) {
    private val loopKickChannel = Channel<Unit>(0)

    private val _events = MutableSharedFlow<Unit>(
        replay = 0, // No events replayed to new subscribers by default
        extraBufferCapacity = 0 // A small buffer can prevent suspension in some cases
    )

    val idle: SharedFlow<Unit> = _events.asSharedFlow()

//    suspend fun addCollaboratorMessage(
//        from: CollaboratorName.Individual,
//        to: CollaboratorName,
//        content: List<com.xemantic.ai.anthropic.content.Content>
//    ) {
//        collaborationStream.emit(MessageAdded(UUID.randomUUID(), from, to, content))
//    }

    suspend fun kickLoop() {
        loopKickChannel.send(Unit)
    }

    suspend fun run() {
        coroutineScope {
            var loopState = LoopState.START
            var convState = AgentConversationState.START
            var lastMessagePosition: Long? = null

            var position: Long? = null

            while (true) {
                ensureActive()

                // Get the loop state up to speed
                while (true) {
                    val entries = gateway.readLoopEvents(position, 10)
                    if (entries.isEmpty()) break
                    for (entry in entries) {
                        loopState = loopState.apply(entry)

//                        val loopEntry = entry.maybeCast(LoopEvent::class)
//                        if (loopEntry != null) {
//                            loopState = loopState.apply(loopEntry)
//                            convState = convState.apply(loopEntry)
//                            // tODO: also agent conversation state
//                        } else {
//                            val conversationEntry = entry.maybeCast(ConversationEvent::class)
//                            if (conversationEntry != null) {
//                                convState.apply(conversationEntry)
//                            }
//                        }
                        // TODO: position should be managed by a builder
                        position = entry.context.storePosition
                    }
                }

                // If the loop is in a state to accept messages, read new messages
                if (loopState.loopStatus.canReceiveMessages) {
                    while (true) {
                        val entries = gateway.readMessageEvents(lastMessagePosition, 10)
                        if (entries.isEmpty()) break
                        for (entry in entries) {
                            convState = convState.apply(entry)
                            lastMessagePosition = entry.context.storePosition
                        }
                    }

                    // Clear out any pending messages base on the loop state
                    convState = convState.clearPendingMessages(loopState.lastIncludedMessageId)
                }

                val stepResult = stepFromState(loopState, convState)

                // If good, run a step from the current state
                if (stepResult == LoopStatus.PENDING) continue

                // wait for an event
                println("Waiting for loop kick")
                _events.emit(Unit)
                loopKickChannel.receive()
                println("Loop kicked, continuing")
            }
        }
    }

    internal suspend fun stepFromState(loopState: LoopState, convState: AgentConversationState): LoopStatus {

        when (loopState.loopStatus) {
            LoopStatus.IN_STEP -> return LoopStatus.STUCK // TODO: somehow show this issue
            LoopStatus.STUCK -> return LoopStatus.STUCK
            LoopStatus.IDLE, LoopStatus.PENDING -> {

                gateway.writeLoopEvents(listOf(LoopEvent.StepStarted()))

                val result: LoopStatus = try {
                    when (loopState.assistantStatus) {
                        AssistantStatus.START, AssistantStatus.IDLE -> runIdle(
                            loopState.assistantContext, convState.pendingMessages
                        )

                        AssistantStatus.TOOL_USE -> runToolUse(/*state.context,*/ loopState.pendingToolUse/*, collabState.pendingContent*/)
                        AssistantStatus.PAUSED -> runPaused(loopState.assistantContext)
                        AssistantStatus.REFUSED -> LoopStatus.STUCK
                    }
                } catch (t: Throwable) {
                    LoopStatus.STUCK
                }

                gateway.writeLoopEvents(listOf(LoopEvent.StepEnded(result)))

                return result
            }
        }
    }

    private suspend fun runToolUse(content: List<Content>): LoopStatus {
        return LoopStatus.STUCK
    }

    private suspend fun runPaused(context: List<Message>): LoopStatus {
        return runAssistantTurn(context)
    }

    private suspend fun runIdle(context: List<Message>, content: List<PendingCollaborationMessage>): LoopStatus {
        if (content.isEmpty()) return LoopStatus.IDLE

        // add a new message from the content
        val message = PreparedMessage.Builder().prepareCollaboratorMessages(content).build(Role.USER)

        return runAssistantTurn(context, message)
    }

    private suspend fun runAssistantTurn(
        context: List<Message>, preparedMessage: PreparedMessage? = null
    ): LoopStatus {

        // create MessageRequest
        val request = _root_ide_package_.com.xemantic.ai.anthropic.message.MessageRequest(
            model = config.model,
            maxTokens = config.maxTokens,
            metadata = config.metadata,
            system = config.system,
            stopSequences = null,
            stream = false,
            temperature = config.temperature,
            toolChoice = config.toolChoice,
            tools = config.tools,
            topK = config.topK,
            topP = config.topP,
            messages = if (preparedMessage == null) context else context + preparedMessage.message
        )

        // send MessageRequest
        when (val response = assistant.sendMessage(request)) {
            is ClaudeApiClient.MessageResult.Message -> {

                val assistantStatus = when (response.message.stopReason) {
                    StopReason.END_TURN -> AssistantStatus.IDLE
                    StopReason.MAX_TOKENS -> AssistantStatus.PAUSED
                    StopReason.STOP_SEQUENCE -> AssistantStatus.IDLE
                    StopReason.TOOL_USE -> AssistantStatus.TOOL_USE
                    StopReason.PAUSE_TURN -> AssistantStatus.PAUSED
                    StopReason.REFUSAL -> AssistantStatus.REFUSED
                    null -> throw IllegalStateException("No stop reason")
                }

                gateway.writeLoopEvents(
                    listOf(
                        LoopEvent.AssistantTurnSucceeded(
                            preparedMessage?.message,
                            response.message,
                            assistantStatus,
                            preparedMessage?.lastSentMessageId
                        )
                    )
                )

                return LoopStatus.PENDING
            }

            is ClaudeApiClient.MessageResult.Error -> {
                gateway.writeLoopEvents(
                    listOf(
                        LoopEvent.AssistantTurnFailed(
                            preparedMessage?.message,
                            response.httpStatus.value,
                            response.httpStatus.description,
                            response.error
                        )
                    )
                )
                return LoopStatus.STUCK
            }
        }
    }
}

@VisibleForTesting
internal fun PreparedMessage.Builder.prepareCollaboratorMessages(list: List<PendingCollaborationMessage>): PreparedMessage.Builder =

    apply {
        for (msg in list) for (content in msg.content) {
            when (content) {
                is Text -> prepareCollaboratorText(msg.from, msg.to, content, msg.id)
                is Document -> prepareCollaboratorDocument(msg.from, msg.to, content, msg.id)
                is Image -> prepareCollaboratorImage(msg.from, msg.to, content, msg.id)
                else -> throw IllegalArgumentException("Unsupported content type: ${content::class.simpleName}")
            }
        }
    }


@VisibleForTesting
internal fun PreparedMessage.Builder.prepareCollaboratorText(
    from: CollaboratorName.Individual, target: CollaboratorName.Channel?, contentBlock: Text, contentId: MessageId
): PreparedMessage.Builder = apply {
    // Find an XML delimiter tag that can be used
    var tag = "collaborator_message"
    while (contentBlock.text.contains("</$tag>")) tag += "_outer"

    val sb = StringBuilder()
    sb.appendLine("Collaborator $from said to ${target ?: "you"}:")
    sb.appendLine("<$tag>")
    sb.appendLine(contentBlock.text)
    sb.append("</$tag>")

    withContent(contentId, Text(sb.toString()))
}


@VisibleForTesting
internal fun PreparedMessage.Builder.prepareCollaboratorDocument(
    collaborator: CollaboratorName.Individual,
    target: CollaboratorName.Channel?,
    contentBlock: Document,
    contentId: MessageId
): PreparedMessage.Builder =

    apply { withContent(contentId, Text("Collaborator $collaborator is sending ${target ?: "you"} a document."), contentBlock) }


@VisibleForTesting
internal fun PreparedMessage.Builder.prepareCollaboratorImage(
    collaborator: CollaboratorName.Individual,
    target: CollaboratorName.Channel?,
    contentBlock: Image,
    contentId: MessageId
): PreparedMessage.Builder =

    apply { withContent(contentId, Text("Collaborator $collaborator is sending ${target ?: "you"} an image."), contentBlock) }


internal class PreparedMessage(val message: Message, val lastSentMessageId: MessageId?) {
    class Builder {
        private val content: MutableList<Content> = mutableListOf()
        private var lastSentMessageId: MessageId? = null

        fun withContent(contentId: MessageId, vararg newContent: Content) = apply {
            content.addAll(newContent)
            lastSentMessageId = contentId
        }

        fun build(role: Role) = PreparedMessage(Message(role, content.toList()), lastSentMessageId)
    }
}

