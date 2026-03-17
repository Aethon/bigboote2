package com.bigboote.events.streams

import com.bigboote.domain.values.AgentId
import com.bigboote.domain.values.AgentTypeId
import com.bigboote.domain.values.ConvId
import com.bigboote.domain.values.EffortId

/**
 * All stream name functions for KurrentDB streams.
 *
 * Stream names follow the pattern from the Event Schema Section 1.5:
 * - /effort:{id} — Effort lifecycle
 * - /effort:{id}/agent:{id} — Agent instance + loop events
 * - /effort:{id}/conv:{id} — Conversation events (@ and # stripped via streamSafeName)
 * - /effort:{id}/docs — Document events
 * - /agenttype:{id} — AgentType configuration
 */
object StreamNames {

    /** Effort lifecycle stream: /effort:{effortId} */
    fun effort(id: EffortId): String =
        "/${id.value}"

    /** Agent instance + loop stream: /effort:{effortId}/agent:{agentId} */
    fun agent(effortId: EffortId, agentId: AgentId): String =
        "/${effortId.value}/${agentId.value}"

    /**
     * Conversation stream: /effort:{effortId}/{convId.streamSafeName}
     *
     * Uses ConvId.streamSafeName which strips @ and # for KurrentDB path safety:
     * - Channel("review") → conv:review
     * - DirectMessage("alice","lead-dev") → conv:alice+lead-dev
     */
    fun conversation(effortId: EffortId, convId: ConvId): String =
        "/${effortId.value}/${convId.streamSafeName}"

    /** Document stream: /effort:{effortId}/docs */
    fun docs(effortId: EffortId): String =
        "/${effortId.value}/docs"

    /** AgentType configuration stream: /{agentTypeId} e.g. /agenttype:lead-eng */
    fun agentType(id: AgentTypeId): String =
        "/${id.value}"
}
