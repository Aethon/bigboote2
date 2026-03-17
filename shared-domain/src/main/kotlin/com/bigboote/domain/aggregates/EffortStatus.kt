package com.bigboote.domain.aggregates

import kotlinx.serialization.Serializable

@Serializable
enum class EffortStatus {
    CREATED,
    ACTIVE,
    PAUSED,
    CLOSED,
}
