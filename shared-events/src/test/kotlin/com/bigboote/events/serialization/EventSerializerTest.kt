package com.bigboote.events.serialization

import com.bigboote.domain.events.EffortEvent.EffortCreated
import com.bigboote.domain.events.EffortEvent.EffortStarted
import com.bigboote.domain.values.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.Clock

/**
 * Tests EventSerializer produces correct KurrentDB EventData.
 */
class EventSerializerTest : StringSpec({

    val now = Clock.System.now()

    "serialize produces EventData with correct eventType" {
        val event = EffortStarted(
            occurredAt = now,
        )
        val eventData = EventSerializer.serialize(event)
        eventData.eventType shouldBe "EffortStarted"
    }

    "serialize produces EventData with JSON payload containing domain fields" {
        val event = EffortCreated(
            name = "Test Effort",
            goal = "Test goal",
            collaborators = listOf(
                CollaboratorSpec(
                    name = CollaboratorName.Individual("alice"),
                    type = CollaboratorType.EXTERNAL,
                ),
            ),
            leadName = CollaboratorName.Individual("alice"),
            createdAt = now,
        )
        val eventData = EventSerializer.serialize(event)
        val payload = String(eventData.eventData, Charsets.UTF_8)

        eventData.eventType shouldBe "EffortCreated"
        payload shouldContain "\"effortId\":\"effort:test123serialize\""
        payload shouldContain "\"name\":\"Test Effort\""
        payload shouldContain "\"goal\":\"Test goal\""
        payload shouldContain "\"@alice\""
    }

    "serialize throws for unregistered event type" {
        data class FakeEvent(val x: Int)
        shouldThrow<IllegalArgumentException> {
            EventSerializer.serialize(FakeEvent(42))
        }
    }
})
