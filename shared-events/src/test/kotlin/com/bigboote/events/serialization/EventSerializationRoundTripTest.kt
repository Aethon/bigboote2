package com.bigboote.events.serialization

import com.bigboote.domain.testing.Events
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import kotlinx.serialization.json.Json

class EventSerializationRoundTripTest : StringSpec({

    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun roundTrip(event: Any) {
        val eventType = EventRegistry.eventTypeOf(event)
        eventType.shouldNotBeEmpty()

        @Suppress("UNCHECKED_CAST")
        val serializer = EventRegistry.serializerFor(eventType)!! as kotlinx.serialization.KSerializer<Any>
        val encoded = json.encodeToString(serializer, event)
        val decoded = json.decodeFromString(serializer, encoded)
        decoded shouldBe event
    }

    Events.cases.forEach { case ->
        "${case.caseName()} round-trip via EventRegistry succeeds" {
            roundTrip(case.subject)
        }
    }
})
