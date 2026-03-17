package com.bigboote.domain.values

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.serialization.json.Json

class EffortIdTest : StringSpec({

    "generate creates id with correct prefix" {
        val id = EffortId.generate()
        id.value shouldStartWith "effort:"
    }

    "rejects id without correct prefix" {
        shouldThrow<IllegalArgumentException> {
            EffortId("invalid:abc")
        }
    }

    "serializes to plain string" {
        val id = EffortId("effort:test123")
        val json = Json.encodeToString(id)
        json shouldBe "\"effort:test123\""
    }

    "deserializes from plain string" {
        val id = Json.decodeFromString<EffortId>("\"effort:test123\"")
        id.value shouldBe "effort:test123"
    }

    "round-trip serialization" {
        val original = EffortId.generate()
        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<EffortId>(json)
        deserialized shouldBe original
    }

    "toString returns value" {
        EffortId("effort:abc").toString() shouldBe "effort:abc"
    }
})
