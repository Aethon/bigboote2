package com.bigboote.domain.aggregates

import kotlinx.serialization.Serializable

@Serializable
enum class AssistantStatus {
    START,
    IDLE,
    PAUSED,
    TOOL_USE,
    REFUSED,
}
