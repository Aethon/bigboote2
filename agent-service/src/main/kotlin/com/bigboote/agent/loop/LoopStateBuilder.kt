//package org.aethon.agentrunner.loop
//
//import com.bigboote.domain.aggregates.LoopState
//import org.aethon.agentrunner.events.EventContext
//
//class LoopStateBuilder :
//    LoopEventVisitor<LoopState, EventContext>() {
//
//    override fun visit(event: LoopEvent, state: LoopState, context: EventContext): LoopState {
//        return super.visit(event, state, context).copy(position = context.position)
//    }
//
//    override fun visitStepStarted(event: StepStarted, state: LoopState, context: EventContext): LoopState {
//        return state.copy(loopStatus = LoopStatus.IN_STEP)
//    }
//
//    override fun visitStepEnded(event: StepEnded, state: LoopState, context: EventContext): LoopState {
//        return state.copy(loopStatus = event.result)
//    }
//
//    override fun visitAssistantTurnSucceeded(
//        event: AssistantTurnSucceeded,
//        state: LoopState,
//        context: EventContext
//    ): LoopState {
//        val newContext = state.context.toMutableList()
//        event.newMessage?.let { newContext.add(it) }
//        newContext.add(event.response.asContextMessage())
//
//        return state.copy(
//            context = newContext.toList(),
//            assistantStatus = event.assistantStatus
//        )
//    }
//
//    override fun visitAssistantTurnFailed(
//        event: AssistantTurnFailed,
//        state: LoopState,
//        context: EventContext
//    ): LoopState {
//        return state
//    }
//
//    override fun visitToolUseRequested(
//        event: ToolUseRequested,
//        state: LoopState,
//        context: EventContext
//    ): LoopState {
//        return state.copy(pendingToolUse = state.pendingToolUse + event.content)
//    }
//
//    override fun visitTaskEndDeclared(
//        event: TaskEndDeclared,
//        state: LoopState,
//        context: EventContext
//    ): LoopState {
//        return state
//    }
//}
