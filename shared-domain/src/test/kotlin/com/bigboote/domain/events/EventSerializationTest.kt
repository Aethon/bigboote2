package com.bigboote.domain.events

import com.bigboote.domain.testing.Events
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class EventSerializationTest : StringSpec({

    val json = Json { encodeDefaults = true }

    fun roundTrip(event: Event) {

        val encoded = json.encodeToString<Event>(event)
        val decoded = json.decodeFromString<Event>(encoded)
        decoded shouldBe event
    }

    Events.cases.forEach { case ->
        "${case.caseName()} round-trip serialization succeeds" {
            roundTrip(case.subject)
        }
    }
})
