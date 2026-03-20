package com.bigboote.domain.events

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class EventSerializationTest : StringSpec({

    val json = Json { encodeDefaults = true }

    fun <T : Event> roundTrip(event: T) {

        val encoded = json.encodeToString<Event>(event)
        val decoded = json.decodeFromString<Event>(encoded)
        decoded shouldBe event
    }

    TestEvents.Examples.forEach { event ->
        "${event::class.simpleName} round-trip serialization succeeds" {
            roundTrip(event)
        }
    }
})
