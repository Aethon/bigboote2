package com.bigboote.domain.values

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class AgentTypeIdTest : StringSpec({

    "accepts simple slug" {
        AgentTypeId("reviewer").value shouldBe "reviewer"
    }

    "accepts slug with numbers" {
        AgentTypeId("agent3").value shouldBe "agent3"
    }

    "accepts multi-segment slug" {
        AgentTypeId("code-review-bot").value shouldBe "code-review-bot"
    }

    "rejects slug with uppercase" {
        shouldThrow<IllegalArgumentException> {
            AgentTypeId("LeadEng")
        }
    }

    "rejects slug with underscores" {
        shouldThrow<IllegalArgumentException> {
            AgentTypeId("lead_eng")
        }
    }

    "rejects slug with leading hyphen" {
        shouldThrow<IllegalArgumentException> {
            AgentTypeId("-leading")
        }
    }

    "rejects slug with trailing hyphen" {
        shouldThrow<IllegalArgumentException> {
            AgentTypeId("trailing-")
        }
    }

    "rejects slug with double hyphen" {
        shouldThrow<IllegalArgumentException> {
            AgentTypeId("double--hyphen")
        }
    }

    "serializes to plain string" {
        val id = AgentTypeId("lead-eng")
        val json = Json.encodeToString(id)
        json shouldBe "\"lead-eng\""
    }

    "round-trip serialization" {
        val original = AgentTypeId("code-reviewer")
        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<AgentTypeId>(json)
        deserialized shouldBe original
    }
})
