//package org.aethon.agentrunner.loop
//
//import com.xemantic.ai.anthropic.content.Content
//import com.xemantic.ai.anthropic.error.ErrorResponse
//import com.xemantic.ai.anthropic.message.Message
//import com.xemantic.ai.anthropic.message.MessageResponse
//import org.aethon.agentrunner.events.Event
//import java.util.UUID
//
///**
// * Describes an event in the agent loop.
// */
//sealed class LoopEvent :
//    Event {
//
//    /**
//     * Accepts a visitor to dispatch this event.
//     *
//     * @param visitor The visitor to dispatch this event to.
//     * @param state The state to pass to the visitor.
//     * @param context The context to pass to the visitor.
//     * @return The state returned by the visitor.
//     */
//    abstract fun <STATE, CONTEXT> accept(
//        visitor: LoopEventVisitor<STATE, CONTEXT>,
//        state: STATE,
//        context: CONTEXT
//    ): STATE
//}
//
///**
// * The agent loop started a step.
// */
//class StepStarted :
//    LoopEvent() {
//
//    // inheritdoc
//    override fun <STATE, CONTEXT> accept(
//        visitor: LoopEventVisitor<STATE, CONTEXT>,
//        state: STATE,
//        context: CONTEXT
//    ): STATE =
//        visitor.visitStepStarted(this, state, context)
//}
//
///**
// * An agent loop step ended.
// *
// * @property result The result of the step.
// */
//data class StepEnded(val result: LoopStatus) :
//    LoopEvent() {
//
//    // inheritdoc
//    override fun <STATE, CONTEXT> accept(
//        visitor: LoopEventVisitor<STATE, CONTEXT>,
//        state: STATE,
//        context: CONTEXT
//    ): STATE =
//        visitor.visitStepEnded(this, state, context)
//}
//
///**
// * The assistant was given a turn in the conversation.
// */
//sealed class AssistantTurnEvent :
//    LoopEvent() {
//
//    /**
//     * The message that was sent to the assistant, if any.
//     *
//     * NOTE: the assistant may be given a turn without a new message to allow it
//     * to resume after a pause response (max_tokens or pause_turn).
//     */
//    abstract val newMessage: Message?
//}
//
///**
// * The loop successfully sent a message to the assistant, and a valid response was received.
// *
// * @property newMessage The message that was sent to the assistant, if any.
// * @property response The response received from the assistant.
// * @property satisfiedContentIds The IDs of the content blocks that were satisfied by this message.
// */
//data class AssistantTurnSucceeded(
//    override val newMessage: Message?,
//    val response: MessageResponse,
//    val assistantStatus: AssistantStatus,
//    val satisfiedContentIds: Set<UUID> = setOf()
//) :
//    AssistantTurnEvent() {
//
//    // inheritdoc
//    override fun <STATE, CONTEXT> accept(
//        visitor: LoopEventVisitor<STATE, CONTEXT>,
//        state: STATE,
//        context: CONTEXT
//    ): STATE =
//        visitor.visitAssistantTurnSucceeded(this, state, context)
//}
//
///**
// * The loop sent a message to the assistant, and the assistant responded with a recognizable error.
// *
// * @property newMessage The message that was sent to the assistant, if any.
// * @property httpStatusCode The HTTP status code of the error response.
// * @property httpStatus The HTTP status of the error response.
// * @property error Details of the error.
// */
//data class AssistantTurnFailed(
//    override val newMessage: Message?,
//    val httpStatusCode: Int,
//    val httpStatus: String,
//    val error: ErrorResponse
//) :
//    AssistantTurnEvent() {
//
//    // inheritdoc
//    override fun <STATE, CONTEXT> accept(
//        visitor: LoopEventVisitor<STATE, CONTEXT>,
//        state: STATE,
//        context: CONTEXT
//    ): STATE =
//        visitor.visitAssistantTurnFailed(this, state, context)
//}
//
/////**
//// * Work was added for the agent loop to handle.
//// */
////sealed class WorkEvent :
////    LoopEvent()
//
/////**
//// * A collaborator added content to be sent to the assistant.
//// *
//// * @property content The content to send to the assistant.
//// */
////data class ContentForAssistantAdded(val content: CollaboratorContent) :
////    AgentWorkEvent() {
////
////    // inheritdoc
////    override fun <STATE, CONTEXT> accept(
////        visitor: AgentEventVisitor<STATE, CONTEXT>,
////        state: STATE,
////        context: CONTEXT
////    ): STATE =
////        visitor.visitModelContentAdded(this, state, context)
////}
////
/////**
//// * The assistant added one or more content blocks to the conversation with a collaborator.
//// */
////data class ContentForCollaboratorAdded(val content: CollaboratorContent) :
////    AgentWorkEvent() {
////
////    // inheritdoc
////    override fun <STATE, CONTEXT> accept(
////        visitor: AgentEventVisitor<STATE, CONTEXT>,
////        state: STATE,
////        context: CONTEXT
////    ): STATE =
////        visitor.visitCollaboratorContentAdded(this, state, context)
////}
//
///**
// * The assistant requested to use a tool.
// *
// * @property content The tool request content blocks.
// */
//data class ToolUseRequested(val content: List<Content>) :
//    LoopEvent() {
//
//    // inheritdoc
//    override fun <STATE, CONTEXT> accept(
//        visitor: LoopEventVisitor<STATE, CONTEXT>,
//        state: STATE,
//        context: CONTEXT
//    ): STATE =
//        visitor.visitToolUseRequested(this, state, context)
//}
//
///**
// * The assistant declared that its task had been completed.
// */
//data class TaskEndDeclared(val placeholder: String = "") :
//    LoopEvent() {
//
//    // inheritdoc
//    override fun <STATE, CONTEXT> accept(
//        visitor: LoopEventVisitor<STATE, CONTEXT>,
//        state: STATE,
//        context: CONTEXT
//    ): STATE =
//        visitor.visitTaskEndDeclared(this, state, context)
//}
