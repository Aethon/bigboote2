package com.bigboote.domain.testing

import com.bigboote.domain.values.AgentId
import com.bigboote.domain.values.AgentTypeId
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.StreamName

/**
 * All example test stream names (except those demonstrating a one-off test case) should be defined here
 * as a "Case".
 */
object StreamNames {

    val effortId = EffortId("V1StGXR8_Z")
    val leadEngTypeId  = AgentTypeId("lead-eng")
    val firstAgentId  = AgentId("K9mPqR2xYw")

    val reviewChannelName = CollaboratorName.Channel("review")
    var leadName = CollaboratorName.Individual("lead")

    val effort = Case(StreamName.Effort(effortId))
    val firstAgent = Case(StreamName.Agent(effortId, firstAgentId))
    val reviewChannel = Case(StreamName.GroupChannel(effortId, reviewChannelName))
    val leadDirectMessages = Case(StreamName.DirectMessage(effortId, leadName))
    val docs = Case(StreamName.Docs(effortId))
    val leadEngAgentType = Case(StreamName.AgentType(leadEngTypeId))

    val cases: List<Case> = listOf(
        effort,
        firstAgent,
        reviewChannel,
        leadDirectMessages,
        docs,
        leadEngAgentType,
    )

    data class Case(
        override val subject: StreamName<*>,
        override val extra: String? = null
    ) : TestCaseItem<StreamName<*>>()
}

