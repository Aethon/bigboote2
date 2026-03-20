package com.bigboote.domain.aggregates

import kotlinx.serialization.Serializable

@Serializable
enum class LoopStatus(val canReceiveMessages: Boolean, val canExecuteSteps: Boolean) {
    /**
     * The loop actively executing a step.
     */
    IN_STEP(canReceiveMessages = false, canExecuteSteps = false),

    /**
     * The loop is idle; it will be triggered by a new event.
     */
    IDLE(canReceiveMessages = true, canExecuteSteps = true),

    /**
     * The loop is not actively executing a step, but it has pending work to complete.
     */
    PENDING(canReceiveMessages = true, canExecuteSteps = true),

    /**
     * The loop is stuck due to some error;
     * it will **not** be triggered by new events until an admin intervenes.
     */
    STUCK(canReceiveMessages = false, canExecuteSteps = false),
}
