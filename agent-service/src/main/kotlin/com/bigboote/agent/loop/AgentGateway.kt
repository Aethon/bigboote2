package org.aethon.agentrunner

import com.bigboote.domain.events.EventLogEntry
import com.bigboote.domain.events.LoopEvent

interface AgentGateway {
    suspend fun readMessageEvents(fromStorePosition: Long?, limit: Int): List<EventLogEntry<*>>

    /**
     * Reads events from the agent's own loop stream.
     */
    suspend fun readLoopEvents(fromStreamPosition: Long?, limit: Int): List<EventLogEntry<LoopEvent>>

    /**
     * Writes a list of events to the agent's own loop stream.
     */
    suspend fun writeLoopEvents(events: List<LoopEvent>): Unit
}
