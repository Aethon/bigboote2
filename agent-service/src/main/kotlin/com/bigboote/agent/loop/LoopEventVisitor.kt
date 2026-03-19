//package org.aethon.agentrunner.loop
//
//import org.aethon.agentrunner.events.EventVisitor
//
///**
// * Visitor pattern for dispatching agent loop events.
// */
//abstract class LoopEventVisitor<STATE, CONTEXT> :
//    EventVisitor<LoopEvent, STATE, CONTEXT>() {
//
//    /**
//     * Dispatches an agent loop event by calling its accept method with this visitor.
//     *
//     * @param event The event to dispatch.
//     * @param state The state to pass to dispatch with the event.
//     * @param context The context to pass to dispatch with the event.
//     * @return The state returned from the dispatch.
//     */
//    override fun visit(event: LoopEvent, state: STATE, context: CONTEXT) = event.accept(this, state, context)
//
//    /**
//     * Handles a [StepStarted] event.
//     *
//     * @param event The event to handle.
//     * @param state The state to use when handling the event.
//     * @param context The context to use when handling the event.
//     * @return The state resulting from handling the event.
//     */
//    abstract fun visitStepStarted(event: StepStarted, state: STATE, context: CONTEXT): STATE
//
//    /**
//     * Handles a [StepEnded] event.
//     *
//     * @param event The event to handle.
//     * @param state The state to use when handling the event.
//     * @param context The context to use when handling the event.
//     * @return The state resulting from handling the event.
//     */
//    abstract fun visitStepEnded(event: StepEnded, state: STATE, context: CONTEXT): STATE
//
//    /**
//     * Handles a [AssistantTurnSucceeded] event.
//     *
//     * @param event The event to handle.
//     * @param state The state to use when handling the event.
//     * @param context The context to use when handling the event.
//     * @return The state resulting from handling the event.
//     */
//    abstract fun visitAssistantTurnSucceeded(event: AssistantTurnSucceeded, state: STATE, context: CONTEXT): STATE
//
//    /**
//     * Handles an [AssistantTurnFailed] event.
//     *
//     * @param event The event to handle.
//     * @param state The state to use when handling the event.
//     * @param context The context to use when handling the event.
//     * @return The state resulting from handling the event.
//     */
//    abstract fun visitAssistantTurnFailed(event: AssistantTurnFailed, state: STATE, context: CONTEXT): STATE
//
//    /**
//     * Handles a [ToolUseRequested] event.
//     *
//     * @param event The event to handle.
//     * @param state The state to use when handling the event.
//     * @param context The context to use when handling the event.
//     * @return The state resulting from handling the event.
//     */
//    abstract fun visitToolUseRequested(event: ToolUseRequested, state: STATE, context: CONTEXT): STATE
//
//    /**
//     * Handles a [TaskEndDeclared] event.
//     *
//     * @param event The event to handle.
//     * @param state The state to use when handling the event.
//     * @param context The context to use when handling the event.
//     * @return The state resulting from handling the event.
//     */
//    abstract fun visitTaskEndDeclared(event: TaskEndDeclared, state: STATE, context: CONTEXT): STATE
//}