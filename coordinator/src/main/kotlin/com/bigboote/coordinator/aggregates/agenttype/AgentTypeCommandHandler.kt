package com.bigboote.coordinator.aggregates.agenttype

import com.bigboote.coordinator.aggregates.AggregateRepository
import com.bigboote.coordinator.api.error.DomainException
import com.bigboote.domain.aggregates.AgentTypeState
import com.bigboote.domain.commands.AgentTypeCommand.CreateAgentType
import com.bigboote.domain.commands.AgentTypeCommand.UpdateAgentType
import com.bigboote.domain.errors.DomainError
import com.bigboote.domain.events.AgentTypeEvent
import com.bigboote.domain.events.AgentTypeEvent.AgentTypeCreated
import com.bigboote.domain.events.AgentTypeEvent.AgentTypeUpdated
import com.bigboote.domain.values.AgentTypeId
import com.bigboote.domain.values.StreamName
import com.bigboote.events.eventstore.ExpectedVersion
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AgentTypeCommandHandlerImpl::class.java)

/**
 * Contract for handling all AgentType aggregate commands.
 * Extracted as an interface so that route-layer tests can mock it without
 * requiring a live EventStore or KurrentDB connection.
 */
interface AgentTypeCommandHandler {
    /** Create a new AgentType. Returns the created [AgentTypeId]. */
    suspend fun handle(cmd: CreateAgentType): AgentTypeId
    /** Update an existing AgentType. */
    suspend fun handle(cmd: UpdateAgentType)
}

/**
 * Handles all AgentType aggregate commands by loading state from the event store,
 * validating the command against current state, producing events, and appending
 * them to the agenttype stream.
 *
 * Stream name pattern: `/agenttype:{slug}` — e.g. `/agenttype:lead-engineer`.
 *
 * [CreateAgentType]: Uses [ExpectedVersion.NoStream] so KurrentDB rejects duplicate
 * IDs at the storage layer (optimistic concurrency). The exception propagates as-is;
 * callers that need a 409 response should catch the storage-layer exception.
 *
 * [UpdateAgentType]: Loads existing state to confirm the AgentType exists, then
 * appends [AgentTypeUpdated] with an [ExpectedVersion.Exact] guard.
 *
 * See Architecture doc Section 6.2 and Event Schema doc Section 3.
 */
class AgentTypeCommandHandlerImpl(
    private val repo: AggregateRepository,
    private val clock: Clock,
) : AgentTypeCommandHandler {

    /**
     * Create a new AgentType. Emits [AgentTypeCreated] on a new stream.
     * Returns the [AgentTypeId] that was created.
     *
     * Throws a storage-layer exception (WrongExpectedVersionException) if a stream
     * with this ID already exists — the client should treat this as a conflict.
     */
    override suspend fun handle(cmd: CreateAgentType): AgentTypeId {
        val event = AgentTypeCreated(
            name          = cmd.name,
            model         = cmd.model,
            systemPrompt  = cmd.systemPrompt,
            maxTokens     = cmd.maxTokens,
            temperature   = cmd.temperature,
            tools         = cmd.tools,
            dockerImage   = cmd.dockerImage,
            spawnStrategy = cmd.spawnStrategy,
            createdAt     = clock.now(),
        )

        repo.append(
            StreamName.AgentType(cmd.agentTypeId),
            listOf(event),
            ExpectedVersion.NoStream,
        )

        logger.info("AgentType created: {}", cmd.agentTypeId)
        return cmd.agentTypeId
    }

    /**
     * Update an existing AgentType. Emits [AgentTypeUpdated].
     *
     * [AgentTypeUpdated] is a partial event — only fields present in the command
     * (non-null) are emitted; absent fields are null, signalling "no change".
     * The projection merges this delta onto the existing read-model row.
     *
     * Does NOT affect running AgentInstances — new configuration takes effect only
     * for instances spawned after this update.
     */
    override suspend fun handle(cmd: UpdateAgentType) {
        val (_, version) = loadAgentType(cmd.agentTypeId)

        val event = AgentTypeUpdated(
            name          = cmd.name,
            model         = cmd.model,
            systemPrompt  = cmd.systemPrompt,
            maxTokens     = cmd.maxTokens,
            temperature   = cmd.temperature,
            tools         = cmd.tools,
            dockerImage   = cmd.dockerImage,
            spawnStrategy = cmd.spawnStrategy,
            updatedAt     = clock.now(),
        )

        repo.append(
            StreamName.AgentType(cmd.agentTypeId),
            listOf(event),
            ExpectedVersion.Exact(version),
        )

        logger.info("AgentType updated: {}", cmd.agentTypeId)
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun loadAgentType(agentTypeId: AgentTypeId): Pair<AgentTypeState, Long> {
        val (state, version) = repo.load(
            StreamName.AgentType(agentTypeId),
            AgentTypeState.EMPTY,
        ) { s, event ->
            if (event is AgentTypeEvent) s.apply(event) else s
        }

        return state to version
    }
}

/**
 * Re-export from error package for convenience within aggregates.
 */
private typealias DomainException = com.bigboote.coordinator.api.error.DomainException
