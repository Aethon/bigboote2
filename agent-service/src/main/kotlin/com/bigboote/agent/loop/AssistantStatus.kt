//package org.aethon.agentrunner.loop
//
//enum class AssistantStatus {
//
//    /**
//     * The assistant has not had a turn yet;
//     * only new content can be sent.
//     */
//    START,
//
//    /**
//     * The assistant has ended its turn;
//     * new content can be sent.
//     */
//    IDLE,
//
//    /**
//     * The assistant paused its turn due to token budget or to break up a long-running turn;
//     * the entire context should be re-sent, but no new content should be added.
//     *
//     * FUTURE: distinguish output token vs. context token failures (context token failures are permanent)
//     */
//    PAUSED,
//
//    /**
//     * The assistant is waiting for tool use to complete;
//     * the results of all pending tool use should be sent,
//     * and new content can be added.
//     */
//    TOOL_USE,
//
//    /**
//     * The assistant refused to continue to prevent a potential policy violation;
//     * the conversation should be ended or forked from a previous turn.
//     */
//    REFUSED
//}