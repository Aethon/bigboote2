package com.bigboote.domain.values

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

class CollaboratorNameTest : StringSpec({

    "from parses individual" {
        val name = CollaboratorName.from("@alice")
        name.shouldBeInstanceOf<CollaboratorName.Individual>()
        name.simple shouldBe "alice"
    }

    "from parses channel" {
        val name = CollaboratorName.from("#review")
        name.shouldBeInstanceOf<CollaboratorName.Channel>()
        name.simple shouldBe "review"
    }

    "from rejects unprefixed string" {
        shouldThrow<IllegalArgumentException> {
            CollaboratorName.from("invalid")
        }
    }

    "individual toString" {
        CollaboratorName.Individual("alice").toString() shouldBe "@alice"
    }

    "channel toString" {
        CollaboratorName.Channel("review").toString() shouldBe "#review"
    }

    "serializes individual as prefixed string" {
        val name: CollaboratorName = CollaboratorName.Individual("alice")
        val json = Json.encodeToString(name)
        json shouldBe "\"@alice\""
    }

    "serializes channel as prefixed string" {
        val name: CollaboratorName = CollaboratorName.Channel("review")
        val json = Json.encodeToString(name)
        json shouldBe "\"#review\""
    }

    "deserializes individual" {
        val name = Json.decodeFromString<CollaboratorName>("\"@alice\"")
        name shouldBe CollaboratorName.Individual("alice")
    }

    "deserializes channel" {
        val name = Json.decodeFromString<CollaboratorName>("\"#review\"")
        name shouldBe CollaboratorName.Channel("review")
    }

    "round-trip serialization" {
        val original: CollaboratorName = CollaboratorName.Individual("lead-dev")
        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<CollaboratorName>(json)
        deserialized shouldBe original
    }
})
