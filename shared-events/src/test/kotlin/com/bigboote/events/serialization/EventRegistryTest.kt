package com.bigboote.events.serialization

import com.bigboote.domain.events.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Tests that all event types are registered in EventRegistry and that
 * the registry's lookup methods work correctly.
 *
 * Uses kotlin-reflect (test dependency only) to enumerate sealed subclasses
 * and verify completeness. If a new event is added to a sealed interface
 * but not registered in EventRegistry, these tests will catch it.
 */
class EventRegistryTest : StringSpec({

    /**
     * Collects all data class subtypes from each sealed interface as Java classes.
     * Uses kotlin-reflect's sealedSubclasses — acceptable in test scope.
     */
    val allSealedEventClasses: Set<Class<*>> = buildSet {
        EffortEvent::class.sealedSubclasses.forEach { add(it.java) }
        AgentEvent::class.sealedSubclasses.forEach { add(it.java) }
        LoopEvent::class.sealedSubclasses.forEach { add(it.java) }
        ConversationEvent::class.sealedSubclasses.forEach { add(it.java) }
        DocumentEvent::class.sealedSubclasses.forEach { add(it.java) }
        AgentTypeEvent::class.sealedSubclasses.forEach { add(it.java) }
    }

    "all sealed event subclasses are registered in EventRegistry" {
        val registeredClasses = EventRegistry.allEventJavaClasses()
        for (klass in allSealedEventClasses) {
            registeredClasses.contains(klass) shouldBe true
        }
    }

    "EventRegistry has no extra registrations beyond sealed event subclasses" {
        val registeredClasses = EventRegistry.allEventJavaClasses()
        registeredClasses.size shouldBe allSealedEventClasses.size
    }

    "all event type names have a matching serializer" {
        for (eventType in EventRegistry.allEventTypes()) {
            EventRegistry.serializerFor(eventType) shouldNotBe null
        }
    }

    "serializerFor returns null for unknown event type" {
        EventRegistry.serializerFor("NonExistentEvent") shouldBe null
    }

    "allEventTypes contains all expected event names" {
        val expectedNames = setOf(
            // Effort events
            "EffortCreated", "EffortStarted", "EffortPaused", "EffortResumed",
            "EffortClosed", "AgentSpawnRequested",
            // Agent events
            "AgentStarted", "AgentStopped", "AgentFailed", "AgentPaused",
            "AgentResumed", "LLMRequestSent", "LLMResponseReceived",
            "ToolInvoked", "ToolResultReceived",
            // Loop events
            "StepStarted", "StepEnded", "AssistantTurnSucceeded",
            "AssistantTurnFailed", "ToolUseRequested", "ConversationMessageReceived",
            // Conversation events
            "ConversationCreated", "MemberAdded", "MessagePosted",
            // Document events
            "DocumentCreated", "DocumentUpdated", "DocumentDeleted",
            // AgentType events
            "AgentTypeCreated", "AgentTypeUpdated",
        )
        EventRegistry.allEventTypes() shouldContainAll expectedNames
    }
})
