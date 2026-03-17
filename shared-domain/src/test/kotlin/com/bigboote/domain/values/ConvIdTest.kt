package com.bigboote.domain.values

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class ConvIdTest : StringSpec({

    "channel value has correct format" {
        val ch = ConvId.channel("review")
        ch.value shouldBe "conv:#review"
    }

    "channel streamSafeName strips #" {
        val ch = ConvId.channel("review")
        ch.streamSafeName shouldBe "conv:review"
    }

    "dm sorts parties alphabetically" {
        val dm = ConvId.dm("bob", "alice")
        dm.value shouldBe "conv:@alice+@bob"
    }

    "dm with already sorted parties" {
        val dm = ConvId.dm("alice", "bob")
        dm.value shouldBe "conv:@alice+@bob"
    }

    "dm streamSafeName strips @" {
        val dm = ConvId.dm("alice", "lead-dev")
        dm.streamSafeName shouldBe "conv:alice+lead-dev"
    }

    "dm constructor rejects unsorted parties" {
        shouldThrow<IllegalArgumentException> {
            ConvId.DirectMessage("bob", "alice")
        }
    }

    "dm constructor accepts sorted parties" {
        val dm = ConvId.DirectMessage("alice", "bob")
        dm.value shouldBe "conv:@alice+@bob"
    }

    "parse channel" {
        val parsed = ConvId.parse("conv:#review")
        parsed shouldBe ConvId.channel("review")
    }

    "parse dm" {
        val parsed = ConvId.parse("conv:@alice+@bob")
        parsed shouldBe ConvId.dm("alice", "bob")
    }

    "parse rejects invalid prefix" {
        shouldThrow<IllegalArgumentException> {
            ConvId.parse("invalid:#review")
        }
    }

    "serializes channel as plain string" {
        val ch: ConvId = ConvId.channel("review")
        val json = Json.encodeToString<ConvId>(ch)
        json shouldBe "\"conv:#review\""
    }

    "serializes dm as plain string" {
        val dm: ConvId = ConvId.dm("alice", "bob")
        val json = Json.encodeToString<ConvId>(dm)
        json shouldBe "\"conv:@alice+@bob\""
    }

    "round-trip serialization for channel" {
        val original: ConvId = ConvId.channel("general")
        val json = Json.encodeToString<ConvId>(original)
        val deserialized = Json.decodeFromString<ConvId>(json)
        deserialized shouldBe original
    }

    "round-trip serialization for dm" {
        val original: ConvId = ConvId.dm("alice", "lead-dev")
        val json = Json.encodeToString<ConvId>(original)
        val deserialized = Json.decodeFromString<ConvId>(json)
        deserialized shouldBe original
    }
})
