package com.bigboote.domain.values

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class AgentTypeIdTest : StringSpec({

    "of() creates valid id from slug" {
        val id = AgentTypeId.of("lead-eng")
        id.value shouldBe "agenttype:lead-eng"
    }

    "accepts simple slug" {
        AgentTypeId("agenttype:reviewer").value shouldBe "agenttype:reviewer"
    }

    "accepts slug with numbers" {
        AgentTypeId("agenttype:agent3").value shouldBe "agenttype:agent3"
    }

    "accepts multi-segment slug" {
        AgentTypeId("agenttype:code-review-bot").value shouldBe "agenttype:code-review-bot"
    }

    "rejects slug with uppercase" {
        shouldThrow<IllegalArgumentException> {
            AgentTypeId("agenttype:LeadEng")
        }
    }

    "rejects slug with underscores" {
        shouldThrow<IllegalArgumentException> {
            AgentTypeId("agenttype:lead_eng")
        }
    }

    "rejects slug with leading hyphen" {
        shouldThrow<IllegalArgumentException> {
            AgentTypeId("agenttype:-leading")
        }
    }

    "rejects slug with trailing hyphen" {
        shouldThrow<IllegalArgumentException> {
            AgentTypeId("agenttype:trailing-")
        }
    }

    "rejects slug with double hyphen" {
        shouldThrow<IllegalArgumentException> {
            AgentTypeId("agenttype:double--hyphen")
        }
    }

    "rejects wrong prefix" {
        shouldThrow<IllegalArgumentException> {
            AgentTypeId("agent:lead-eng")
        }
    }

    "serializes to plain string" {
        val id = AgentTypeId.of("lead-eng")
        val json = Json.encodeToString(id)
        json shouldBe "\"agenttype:lead-eng\""
    }

    "round-trip serialization" {
        val original = AgentTypeId.of("code-reviewer")
        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<AgentTypeId>(json)
        deserialized shouldBe original
    }
})
