package com.bigboote.events.streams

import com.bigboote.domain.values.AgentId
import com.bigboote.domain.values.AgentTypeId
import com.bigboote.domain.values.ConvId
import com.bigboote.domain.values.EffortId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class StreamNamesTest : StringSpec({

    val effortId = EffortId("effort:V1StGXR8_Z")
    val agentId = AgentId("agent:K9mPqR2xYw")

    "effort stream name" {
        StreamNames.effort(effortId) shouldBe "/effort:V1StGXR8_Z"
    }

    "agent stream name" {
        StreamNames.agent(effortId, agentId) shouldBe "/effort:V1StGXR8_Z/agent:K9mPqR2xYw"
    }

    "conversation stream name for Channel" {
        val convId = ConvId.channel("review")
        StreamNames.conversation(effortId, convId) shouldBe "/effort:V1StGXR8_Z/conv:review"
    }

    "conversation stream name for DirectMessage strips @ signs" {
        val convId = ConvId.dm("alice", "lead-dev")
        StreamNames.conversation(effortId, convId) shouldBe "/effort:V1StGXR8_Z/conv:alice+lead-dev"
    }

    "conversation stream name for DM sorts names alphabetically" {
        val convId = ConvId.dm("lead-dev", "alice")
        StreamNames.conversation(effortId, convId) shouldBe "/effort:V1StGXR8_Z/conv:alice+lead-dev"
    }

    "docs stream name" {
        StreamNames.docs(effortId) shouldBe "/effort:V1StGXR8_Z/docs"
    }

    "agentType stream name" {
        val agentTypeId = AgentTypeId.of("lead-eng")
        StreamNames.agentType(agentTypeId) shouldBe "/agenttype:lead-eng"
    }
})
