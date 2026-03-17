package com.bigboote.domain.aggregates

import kotlinx.serialization.Serializable

@Serializable
enum class AgentStatus {
    STARTED,
    STOPPED,
    FAILED,
    PAUSED,
    RESUMED,
}
