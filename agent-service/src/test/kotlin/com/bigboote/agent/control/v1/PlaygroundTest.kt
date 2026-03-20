package com.bigboote.agent.control.v1

import com.bigboote.domain.events.DirectMessageEvent
import com.bigboote.domain.events.EventLogEntry
import com.bigboote.domain.events.LoopEvent
import com.bigboote.domain.values.*
import com.xemantic.ai.anthropic.Anthropic
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import org.aethon.agentrunner.AgentGateway
import org.aethon.agentrunner.AgentLoopConfig
import org.aethon.agentrunner.AgentLoopStepper
import org.aethon.anthropic.FileCredentials
import org.junit.jupiter.api.Test

class PlaygroundTest {

    @Test
    fun `loop it`() {

        val credentialsFile = Path(System.getProperty("user.home"), ".bigbootek", "credentials.json")
        val client = Anthropic(FileCredentials(credentialsFile)) {}

        val config = AgentLoopConfig(
            model = "claude-haiku-4-5-20251001",
            maxTokens = 20
        )

        val eventLog = LocalEventLog().apply {
            onNewEvent {
                println(it)
            }
        }

        val gateway = TestingGateway(eventLog)

        val collabStream = eventLog.getStream(
            StreamName.DirectMessage(EffortId("abd"), CollaboratorName.Individual("ffg")),
            DirectMessageEvent::class
        )

        runBlocking {
            collabStream.emit(
                DirectMessageEvent.DirectMessagePosted(
                    MessageId.generate(),
                    CollaboratorName.Individual("stakeholder"),
                    "You're name on this team is `@agent`. Collaborators are named with an `@` prefix. Channels have multiple collaborators and are prefixed with `#`, like `#work`."
                )
            )
        }

        val messages = ArrayDeque(
            listOf(
                DirectMessageEvent.DirectMessagePosted(
                    MessageId.generate(),
                    CollaboratorName.Individual("stakeholder"),
                    "Say hello to me"
                ),
                DirectMessageEvent.DirectMessagePosted(
                    MessageId.generate(),
                    CollaboratorName.Individual("manager"),
                    "Say hello to me, too"
                ),
                DirectMessageEvent.DirectMessagePosted(
                    MessageId.generate(),
                    CollaboratorName.Individual("manager"),
                    "Work channel, welcome @agent"
                )
            )
        )

        val stepper = AgentLoopStepper(gateway, client, config)

        runBlocking {

            launch {
                stepper.idle.collect {
                    if (messages.isNotEmpty()) {
                        collabStream.emit(messages.removeFirst())
                        println("Added message and kicking loop")
                        stepper.kickLoop()
                    }
                }
            }
            stepper.run()
        }
    }
}

class TestingGateway(eventLog: LocalEventLog): AgentGateway {

    val loopStream = eventLog.getStream(StreamName.Loop(EffortId("abd"), AgentId("ffg")), LoopEvent::class)

    val collabStream = eventLog.getStream(
        StreamName.DirectMessage(EffortId("abd"), CollaboratorName.Individual("ffg")),
        DirectMessageEvent::class
    )

    override suspend fun readMessageEvents(fromStorePosition: Long?, limit: Int): List<EventLogEntry<*>> {
        return collabStream.readAfter(fromStorePosition, limit)
    }

    /**
     * Writes a list of events to the agent's own loop stream.
     */
    override suspend fun writeLoopEvents(events: List<LoopEvent>): Unit {
        loopStream.emit(*events.toTypedArray())
    }

    /**
     * Reads events from the agent's own loop stream.
     */
    override suspend fun readLoopEvents(fromStreamPosition: Long?, limit: Int): List<EventLogEntry<LoopEvent>> {
        return loopStream.readAfter(fromStreamPosition, limit)
    }
}