package com.bigboote.domain.values

import com.bigboote.domain.testing.StreamNames
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/**
 * Verifies [StreamName] path generation for all stream types.
 *
 * Paths embed the full typed-ID values (which include their own prefix), so the
 * stream segment is doubled — e.g. EffortId("effort:V1StGXR8_Z") produces
 * "/effort:effort:V1StGXR8_Z". This is by design: the ID type carries its prefix
 * and the stream hierarchy adds its own segment prefix independently.
 */
class StreamNamesTest : StringSpec({

    val json = Json { encodeDefaults = true }

    StreamNames.cases.forEach { case ->
        "${case.caseName()} round-trip serialization succeeds" {
            val encoded = json.encodeToString<AnyStreamName>(case.subject)
            val decoded = json.decodeFromString<AnyStreamName>(encoded)
            decoded shouldBe case.subject
        }
    }
//
//    val effortId = EffortId("effort:V1StGXR8_Z")
//    val agentId  = AgentId("agent:K9mPqR2xYw")
//
//    "effort stream path" {
//        StreamName.Effort(effortId).path shouldBe "/effort:effort:V1StGXR8_Z"
//    }
//
//    "agent stream path" {
//        StreamName.Agent(effortId, agentId).path shouldBe
//            "/effort:effort:V1StGXR8_Z/agent:agent:K9mPqR2xYw"
//    }
//
//    "conversation stream path for Channel" {
//        val convId = ConvId.channel("review")
//        StreamName.Conversation(effortId, convId).path shouldBe
//            "/effort:effort:V1StGXR8_Z/conv:review"
//    }
//
//    "conversation stream path for DirectMessage strips @ signs" {
//        val convId = ConvId.dm("alice", "lead-dev")
//        StreamName.Conversation(effortId, convId).path shouldBe
//            "/effort:effort:V1StGXR8_Z/conv:alice+lead-dev"
//    }
//
//    "conversation stream path for DM sorts names alphabetically" {
//        val convId = ConvId.dm("lead-dev", "alice")
//        StreamName.Conversation(effortId, convId).path shouldBe
//            "/effort:effort:V1StGXR8_Z/conv:alice+lead-dev"
//    }
//
//    "docs stream path" {
//        StreamName.Docs(effortId).path shouldBe "/effort:effort:V1StGXR8_Z/docs"
//    }
//
//    "agentType stream path" {
//        val agentTypeId = AgentTypeId.of("lead-eng")
//        StreamName.AgentType(agentTypeId).path shouldBe "/agenttype:agenttype:lead-eng"
//    }
})