package com.bigboote.agent.control.v1

import com.bigboote.domain.values.AgentId
import com.bigboote.domain.values.AgentTypeId
import com.bigboote.domain.values.EffortId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * Stub handler for the Agent Control API.
 *
 * In Phase 9 (loop integration) this will delegate start/pause/resume/stop
 * to AgentLoopStepper. For now it manages a simple state machine:
 * starting -> running -> paused/stopping/stopped.
 */
class AgentControlHandler {

    private val logger = LoggerFactory.getLogger(AgentControlHandler::class.java)

    // DECISION: Use simple atomic state for the Phase 8 stub. Phase 9 replaces this
    // with real AgentLoopStepper delegation.
    private val loopState = AtomicReference("starting")
    private val instanceId = AtomicReference<AgentId?>(null)
    private val effortId = AtomicReference<EffortId?>(null)
    private val agentTypeId = AtomicReference<AgentTypeId?>(null)
    private val currentTurn = AtomicReference(0)
    private val lastActivityAt = AtomicReference<Instant?>(null)
    private val streamPosition = AtomicReference(0L)

    fun status(): StatusResponse {
        return StatusResponse(
            instanceId = instanceId.get() ?: AgentId("uninitialized"),
            effortId = effortId.get() ?: EffortId("uninitialized"),
            agentTypeId = agentTypeId.get() ?: AgentTypeId("unknown"),
            loopState = loopState.get(),
            currentTurn = currentTurn.get(),
            lastActivityAt = lastActivityAt.get()?.toString(),
            streamPosition = streamPosition.get(),
        )
    }

    fun start(request: StartRequest): StartResponse {
        logger.info("Starting agent loop for instance={}, effort={}", request.instanceId, request.effortId)
        instanceId.set(request.instanceId)
        effortId.set(request.effortId)
        agentTypeId.set(request.agentTypeId)
        loopState.set("running")
        lastActivityAt.set(Clock.System.now())
        return StartResponse(started = true, instanceId = request.instanceId)
    }

    fun pause(): AckResponse {
        val id = instanceId.get() ?: AgentId("uninitialized")
        logger.info("Pausing agent loop for instance={}", id)
        loopState.set("paused")
        lastActivityAt.set(Clock.System.now())
        return AckResponse(success = true, instanceId = id)
    }

    fun resume(): AckResponse {
        val id = instanceId.get() ?: AgentId("uninitialized")
        logger.info("Resuming agent loop for instance={}", id)
        loopState.set("running")
        lastActivityAt.set(Clock.System.now())
        return AckResponse(success = true, instanceId = id)
    }

    fun stop(): AckResponse {
        val id = instanceId.get() ?: AgentId("uninitialized")
        logger.info("Stopping agent loop for instance={}", id)
        loopState.set("stopped")
        lastActivityAt.set(Clock.System.now())
        return AckResponse(success = true, instanceId = id)
    }
}
