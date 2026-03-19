package com.bigboote.domain.events

sealed interface StreamName {
    val type: String
}

sealed interface IdentifiedStreamName :StreamName {
    override val type: String
    val id: String
}

data class EffortStreamName(override val id: String) : StreamName {
    override val type: String = "effort"

    override fun toString(): String = "/$type:$id"
}

data class AgentStreamName(override val id: String, val effort: EffortStreamName) : StreamName {
    override val type: String = "agent"

    override fun toString(): String = "$effort/$type:$id"
}

data class AgentTypeStreamName(override val id: String) : StreamName {
    override val type: String = "agent-type"

    override fun toString(): String = "$type:$id"
}

data class ConversationStreamName(override val id: String, val effort: EffortStreamName) : StreamName {
    override val type: String = "conversation"

    override fun toString(): String = "$effort/$type:$id"
}