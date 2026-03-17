package com.bigboote.events.serialization

import com.bigboote.domain.events.AgentEvent.*
import com.bigboote.domain.events.AgentTypeEvent.*
import com.bigboote.domain.events.ConversationEvent.*
import com.bigboote.domain.events.DocumentEvent.*
import com.bigboote.domain.events.EffortEvent.*
import com.bigboote.domain.events.LoopEvent.*
import kotlinx.serialization.KSerializer

/**
 * Maps event type names (the KurrentDB eventType field) to their KSerializer.
 * All events from shared-domain must be registered here. A test enforces completeness.
 */
object EventRegistry {

    private data class Registration(
        val eventType: String,
        val serializer: KSerializer<out Any>,
        val javaClass: Class<out Any>,
    )

    private val registrations: List<Registration> = listOf(
        // Effort events
        Registration("EffortCreated", EffortCreated.serializer(), EffortCreated::class.java),
        Registration("EffortStarted", EffortStarted.serializer(), EffortStarted::class.java),
        Registration("EffortPaused", EffortPaused.serializer(), EffortPaused::class.java),
        Registration("EffortResumed", EffortResumed.serializer(), EffortResumed::class.java),
        Registration("EffortClosed", EffortClosed.serializer(), EffortClosed::class.java),
        Registration("AgentSpawnRequested", AgentSpawnRequested.serializer(), AgentSpawnRequested::class.java),

        // Agent events
        Registration("AgentStarted", AgentStarted.serializer(), AgentStarted::class.java),
        Registration("AgentStopped", AgentStopped.serializer(), AgentStopped::class.java),
        Registration("AgentFailed", AgentFailed.serializer(), AgentFailed::class.java),
        Registration("AgentPaused", AgentPaused.serializer(), AgentPaused::class.java),
        Registration("AgentResumed", AgentResumed.serializer(), AgentResumed::class.java),
        Registration("LLMRequestSent", LLMRequestSent.serializer(), LLMRequestSent::class.java),
        Registration("LLMResponseReceived", LLMResponseReceived.serializer(), LLMResponseReceived::class.java),
        Registration("ToolInvoked", ToolInvoked.serializer(), ToolInvoked::class.java),
        Registration("ToolResultReceived", ToolResultReceived.serializer(), ToolResultReceived::class.java),

        // Loop events
        Registration("StepStarted", StepStarted.serializer(), StepStarted::class.java),
        Registration("StepEnded", StepEnded.serializer(), StepEnded::class.java),
        Registration("AssistantTurnSucceeded", AssistantTurnSucceeded.serializer(), AssistantTurnSucceeded::class.java),
        Registration("AssistantTurnFailed", AssistantTurnFailed.serializer(), AssistantTurnFailed::class.java),
        Registration("ToolUseRequested", ToolUseRequested.serializer(), ToolUseRequested::class.java),
        Registration("ConversationMessageReceived", ConversationMessageReceived.serializer(), ConversationMessageReceived::class.java),

        // Conversation events
        Registration("ConversationCreated", ConversationCreated.serializer(), ConversationCreated::class.java),
        Registration("MemberAdded", MemberAdded.serializer(), MemberAdded::class.java),
        Registration("MessagePosted", MessagePosted.serializer(), MessagePosted::class.java),

        // Document events
        Registration("DocumentCreated", DocumentCreated.serializer(), DocumentCreated::class.java),
        Registration("DocumentUpdated", DocumentUpdated.serializer(), DocumentUpdated::class.java),
        Registration("DocumentDeleted", DocumentDeleted.serializer(), DocumentDeleted::class.java),

        // AgentType events
        Registration("AgentTypeCreated", AgentTypeCreated.serializer(), AgentTypeCreated::class.java),
        Registration("AgentTypeUpdated", AgentTypeUpdated.serializer(), AgentTypeUpdated::class.java),
    )

    private val byName: Map<String, Registration> =
        registrations.associateBy { it.eventType }

    private val byClass: Map<Class<out Any>, Registration> =
        registrations.associateBy { it.javaClass }

    /** Returns the serializer for a given event type name, or null if not registered. */
    fun serializerFor(eventType: String): KSerializer<out Any>? =
        byName[eventType]?.serializer

    /** Returns the event type name for a given event instance. */
    fun eventTypeOf(event: Any): String =
        byClass[event.javaClass]?.eventType
            ?: throw IllegalArgumentException("Unregistered event type: ${event.javaClass.name}")

    /** Returns all registered event type names. */
    fun allEventTypes(): Set<String> = byName.keys

    /** Returns all registered Java classes. */
    fun allEventJavaClasses(): Set<Class<out Any>> = byClass.keys
}
