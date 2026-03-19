//package org.aethon.agentrunner.loop
//
//enum class LoopStatus(
//    val canReceiveCollaborationEvents: Boolean,
//    val canExecuteSteps: Boolean) {
//
//    /**
//     * The loop actively executing a step.
//     */
//    IN_STEP(false, false),
//
//    /**
//     * The loop is idle; it will be triggered by a new event.
//     */
//    IDLE(true, true),
//
//    /**
//     * The loop is not actively executing a step, but it has pending work to complete.
//     */
//    PENDING(true, true),
//
//    /**
//     * The loop is stuck due to some error;
//     * it will **not** be triggered by new events until an admin intervenes.
//     */
//    STUCK(false, false)
//}
