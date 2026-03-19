package com.bigboote.events.streams

import com.bigboote.domain.values.AgentId
import com.bigboote.domain.values.AgentTypeId
import com.bigboote.domain.values.ConvId
import com.bigboote.domain.values.EffortId

/**
 * All stream name functions for KurrentDB streams.
 *
 * @deprecated Use [com.bigboote.domain.values.StreamName] hierarchy instead.
 * This object pre-dates the typed stream name system and will be removed in a future phase.
 *
 * Stream names follow the pattern from the Event Schema Section 1.5:
 * - /effort:{id} — Effort lifecycle
 * - /effort:{id}/agent:{id} — Agent instance + loop events
 * - /effort:{id}/conv:{id} — Conversation events (@ and # stripped via streamSafeName)
 * - /effort:{id}/docs — Document events
 * - /agenttype:{id} — AgentType configuration
 */
@Deprecated("Use StreamName hierarchy from com.bigboote.domain.values instead")
object StreamNames {

    /** Effort lifecycle stream: /effort:{effortId} */
    @Deprecated(
        "Use StreamName.Effort(id).path instead",
        replaceWith = ReplaceWith("StreamName.Effort(id).path", "com.bigboote.domain.values.StreamName"),
    )
    fun effort(id: EffortId): String =
        "/${id.value}"

    /** Agent instance + loop stream: /effort:{effortId}/agent:{agentId} */
    @Deprecated(
        "Use StreamName.Agent(effortId, agentId).path instead",
        replaceWith = ReplaceWith(
            "StreamName.Agent(effortId, agentId).path",
            "com.bigboote.domain.values.StreamName",
        ),
    )
    fun agent(effortId: EffortId, agentId: AgentId): String =
        "/${effortId.value}/${agentId.value}"

    /**
     * Conversation stream: /effort:{effortId}/{convId.streamSafeName}
     *
     * @deprecated Use StreamName.Conversation(effortId, convId).path instead.
     */
    @Deprecated(
        "Use StreamName.Conversation(effortId, convId).path instead",
        replaceWith = ReplaceWith(
            "StreamName.Conversation(effortId, convId).path",
            "com.bigboote.domain.values.StreamName",
        ),
    )
    fun conversation(effortId: EffortId, convId: ConvId): String =
        "/${effortId.value}/${convId.streamSafeName}"

    /** Document stream: /effort:{effortId}/docs */
    @Deprecated(
        "Use StreamName.Docs(effortId).path instead",
        replaceWith = ReplaceWith(
            "StreamName.Docs(effortId).path",
            "com.bigboote.domain.values.StreamName",
        ),
    )
    fun docs(effortId: EffortId): String =
        "/${effortId.value}/docs"

    /** AgentType configuration stream: /{agentTypeId} e.g. /agenttype:lead-eng */
    @Deprecated(
        "Use StreamName.AgentType(id).path instead",
        replaceWith = ReplaceWith(
            "StreamName.AgentType(id).path",
            "com.bigboote.domain.values.StreamName",
        ),
    )
    fun agentType(id: AgentTypeId): String =
        "/${id.value}"
}
