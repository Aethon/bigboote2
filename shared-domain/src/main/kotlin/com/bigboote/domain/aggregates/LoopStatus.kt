package com.bigboote.domain.aggregates

import kotlinx.serialization.Serializable

@Serializable
enum class LoopStatus(val canReceiveMessages: Boolean, val canExecuteSteps: Boolean) {
    IN_STEP(canReceiveMessages = false, canExecuteSteps = false),
    IDLE(canReceiveMessages = true, canExecuteSteps = true),
    PENDING(canReceiveMessages = true, canExecuteSteps = true),
    STUCK(canReceiveMessages = false, canExecuteSteps = false),
}
