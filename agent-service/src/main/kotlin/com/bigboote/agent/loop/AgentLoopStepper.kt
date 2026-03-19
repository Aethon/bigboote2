package org.aethon.agentrunner

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
import org.aethon.agentrunner.collaboration.PendingCollaborationMessage
import com.bigboote.domain.aggregates.LoopState
import com.bigboote.domain.aggregates.LoopStatus
import com.bigboote.domain.events.ConversationEvent
import com.bigboote.domain.events.Event
import com.bigboote.domain.events.StreamBasedState
import com.bigboote.domain.events.EventStream
import com.bigboote.domain.events.EventStreamReader
import com.bigboote.domain.events.LoopEvent
import com.bigboote.domain.events.readAfter
import com.bigboote.domain.values.CollaboratorName
import org.jetbrains.annotations.VisibleForTesting
import java.util.UUID
import kotlin.String
import kotlin.time.Clock

suspend inline fun <reified EVENT: Event, STATE: StreamBasedState<EVENT>> STATE.applyEvents(
    streamReader: EventStreamReader,
    state: STATE
): STATE {
    var currentState = state
    while (true) {
        val entries = streamReader.readAfter<EVENT>(currentState.position, 10)
        if (entries.isEmpty())
            return currentState
        for (entry in entries)
            currentState = applyEvent(entry.event, entry.context) as STATE
    }
}

class AgentLoopStepper(
    private val agent: CollaboratorName.Individual,
    private val assistant: ClaudeApiClient,
    private val config: AgentLoopConfig,
    private val loopStream: EventStream,
    private val conversationsStream: EventStream,
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

            while (true) {
                ensureActive()

                // Get the loop state up to speed
                while (true) {
                    val entries = loopStream.readAfter<LoopEvent>(loopState.position, 10)
                    if (entries.isEmpty())
                        break
                    for (entry in entries)
                        loopState = loopState.applyEvent(entry.event, entry.context)
                }

                // If the loop is in a state to accept messages, read new messages
                if (loopState.loopStatus.canReceiveMessages) {
                    var lastMessagePosition = loopState.lastMessagePosition
                    while (true) {
                        val entries = conversationsStream.readAfter<ConversationEvent>(loopState.position, 10)
                        if (entries.isEmpty())
                            break
                        for (entry in entries) {
                            when (entry.event) {
                                is ConversationEvent.MessagePosted -> {
                                    loopStream.emit(LoopEvent.ConversationMessageReceived(entry.even))
                                }
                            }
                            loopStream.emit(LoopEvent.ConversationMessageReceived(entry.event.))
                            loopState = loopState.applyEvent(entry.event, entry.context)
                        }
                    }

                }
                val stepResult = stepFromState(loopState)

                // If good, run a step from the current state
                if (stepResult == LoopStatus.PENDING)
                    continue

                // wait for an event
                println("Waiting for loop kick")
                _events.emit(Unit)
                loopKickChannel.receive()
                println("Loop kicked, continuing")
            }
        }
    }

    internal suspend fun stepFromState(loopState: LoopState): LoopStatus {

        when (loopState.loopStatus) {
            LoopStatus.IN_STEP -> return LoopStatus.STUCK // TODO: somehow show this issue
            LoopStatus.STUCK -> return LoopStatus.STUCK
            LoopStatus.IDLE, LoopStatus.PENDING -> {

                loopStream.emit(LoopEvent.StepStarted(clock.now()))

                val result: LoopStatus = try {
                    when (loopState.assistantStatus) {
                        AssistantStatus.START, AssistantStatus.IDLE -> runIdle(
                            loopState.assistantContext,
                            collabState.pendingContent
                        )

                        AssistantStatus.TOOL_USE -> runToolUse(/*state.context,*/ loopState.pendingToolUse/*, collabState.pendingContent*/)
                        AssistantStatus.PAUSED -> runPaused(loopState.assistantContext)
                        AssistantStatus.REFUSED -> LoopStatus.STUCK
                    }
                } catch (t: Throwable) {
                    LoopStatus.STUCK
                }

                loopStream.emit(LoopEvent.StepEnded(result, clock.now()))

                return result
            }
        }
    }

    private suspend fun runToolUse(content: List<Content>): LoopStatus {
        return LoopStatus.STUCK
    }

    private suspend fun runPaused(context: List<com.xemantic.ai.anthropic.message.Message>): LoopStatus {
        return runAssistantTurn(context)
    }

    private suspend fun runIdle(context: List<com.xemantic.ai.anthropic.message.Message>, content: List<PendingCollaborationMessage>): LoopStatus {
        if (content.isEmpty())
            return LoopStatus.IDLE

        // add a new message from the content
        val message = PreparedMessage.Builder().prepareCollaboratorMessages(content).build(_root_ide_package_.com.xemantic.ai.anthropic.message.Role.USER)

        return runAssistantTurn(context, message)
    }

    private suspend fun runAssistantTurn(
        context: List<com.xemantic.ai.anthropic.message.Message>,
        preparedMessage: PreparedMessage? = null
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

                loopStream.emit(
                    LoopEvent.AssistantTurnSucceeded(
                        preparedMessage?.message,
                        response.message,
                        assistantStatus,
                        preparedMessage?.satisfiedContentIds ?: emptySet()
                    )
                )

//                // TODO: fix dual write issue (prolly hop the message from the loop event)
//                if (preparedMessage != null && preparedMessage.satisfiedContentIds.isNotEmpty())
//                    collaborationStream.emit(MessagesSentToAssistant(agent, preparedMessage.satisfiedContentIds))

                return LoopStatus.PENDING
            }

            is ClaudeApiClient.MessageResult.Error -> {
                loopStream.emit(
                    LoopEvent.AssistantTurnFailed(
                        preparedMessage?.message,
                        response.httpStatus.value,
                        response.httpStatus.description,
                        response.error
                    )
                )
                return LoopStatus.STUCK
            }
        }
    }
}

@VisibleForTesting
internal fun PreparedMessage.Builder.prepareCollaboratorMessages(list: List<PendingCollaborationMessage>):
        PreparedMessage.Builder =

    apply {
        for (collab in list)
            for (content in collab.content)
                when (content) {
                    is Text -> prepareCollaboratorText(collab.from, collab.to, content, collab.id)
                    is Document -> prepareCollaboratorDocument(collab.from, collab.to, content, collab.id)
                    is Image -> prepareCollaboratorImage(collab.from, collab.to, content, collab.id)
                    else -> throw IllegalArgumentException("Unsupported content type: ${content::class.simpleName}")
                }
    }


@VisibleForTesting
internal fun PreparedMessage.Builder.prepareCollaboratorText(
    from: String,
    target: String,
    contentBlock: com.xemantic.ai.anthropic.content.Text,
    contentId: UUID
):
        PreparedMessage.Builder =
    apply {
        // Find an XML delimiter tag that can be used
        var tag = "collaborator_message"
        while (contentBlock.text.contains("</$tag>"))
            tag += "_outer"

        val sb = StringBuilder()
        sb.appendLine("Collaborator @$from said to $target:")
        sb.appendLine("<$tag>")
        sb.appendLine(contentBlock.text)
        sb.append("</$tag>")

        withContent(contentId, Text(sb.toString()))
    }


@VisibleForTesting
internal fun PreparedMessage.Builder.prepareCollaboratorDocument(
    collaborator: String,
    target: String,
    contentBlock: Document,
    contentId: UUID
):
        PreparedMessage.Builder =

    apply { withContent(contentId, Text("Collaborator $collaborator is sending $target a document."), contentBlock) }


@VisibleForTesting
internal fun PreparedMessage.Builder.prepareCollaboratorImage(
    collaborator: String,
    target: String,
    contentBlock: Image,
    contentId: UUID
):
        PreparedMessage.Builder =

    apply { withContent(contentId, Text("Collaborator $collaborator is sending $target an image."), contentBlock) }


internal class PreparedMessage(val message: Message, val satisfiedContentIds: Set<UUID>) {
    class Builder {
        private val content: MutableList<Content> = mutableListOf()
        private val satisfiedContentIs: MutableSet<UUID> = mutableSetOf()

        fun withContent(contentId: UUID, vararg newContent: Content) = apply {
            content.addAll(newContent)
            satisfiedContentIs.add(contentId)
        }

        fun build(role: Role) = PreparedMessage(Message(role, content.toList()), satisfiedContentIs.toSet())
    }
}

