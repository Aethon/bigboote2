package com.bigboote.domain.values

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.serialization.json.Json

class EffortIdTest : StringSpec({

    "serializes to plain string" {
        val id = EffortId("test123")
        val json = Json.encodeToString(id)
        json shouldBe "\"test123\""
    }

    "deserializes from plain string" {
        val id = Json.decodeFromString<EffortId>("\"test123\"")
        id.value shouldBe "test123"
    }

    "round-trip serialization" {
        val original = EffortId.generate()
        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<EffortId>(json)
        deserialized shouldBe original
    }

    "toString returns value" {
        EffortId("abc").toString() shouldBe "abc"
    }
})
