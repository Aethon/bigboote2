package com.bigboote.coordinator.aggregates.effort

import com.bigboote.coordinator.aggregates.AggregateRepository
import com.bigboote.domain.aggregates.EffortState
import com.bigboote.domain.aggregates.EffortStatus
import com.bigboote.domain.commands.EffortCommand.*
import com.bigboote.domain.errors.DomainError
import com.bigboote.domain.events.EffortEvent
import com.bigboote.domain.events.EffortEvent.*
import com.bigboote.domain.values.AgentId
import com.bigboote.domain.values.CollaboratorType
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.StreamName
import com.bigboote.events.eventstore.ExpectedVersion
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("com.bigboote.coordinator.aggregates.effort.EffortCommandHandlerImpl")

/**
 * Handles all Effort aggregate commands.
 *
 * Extracted so that tests can mock it without requiring the `open` modifier.
 * The sole production implementation is [EffortCommandHandlerImpl].
 *
 * See Architecture doc Section 6.2.
 */
interface EffortCommandHandler {
    suspend fun handle(cmd: CreateEffort): EffortId
    suspend fun handle(cmd: StartEffort)
    suspend fun handle(cmd: PauseEffort)
    suspend fun handle(cmd: ResumeEffort)
    suspend fun handle(cmd: CloseEffort)
}

/**
 * Production implementation of [EffortCommandHandler].
 * Loads state from the event store, validates the command, and appends events.
 *
 * See Architecture doc Section 6.2.
 */
class EffortCommandHandlerImpl(
    private val repo: AggregateRepository,
    private val clock: Clock,
) : EffortCommandHandler {

    /**
     * Create a new Effort. Emits [EffortCreated] on a new stream.
     * Returns the generated EffortId.
     */
    override suspend fun handle(cmd: CreateEffort): EffortId {
        val event = EffortCreated(
            name = cmd.name,
            goal = cmd.goal,
            collaborators = cmd.collaborators,
            leadName = cmd.leadName,
            createdAt = clock.now(),
        )

        repo.append(
            StreamName.Effort(cmd.effortId),
            listOf(event),
            ExpectedVersion.NoStream,
        )

        logger.info("Effort created: {}", cmd.effortId)
        return cmd.effortId
    }

    /**
     * Start an Effort. Emits [EffortStarted] plus [AgentSpawnRequested] for
     * each AGENT collaborator. Effort must be in CREATED status.
     */
    override suspend fun handle(cmd: StartEffort) {
        val (state, version) = maybeLoadEffort(cmd.effortId) ?: throw DomainException(DomainError.EffortNotFound(cmd.effortId))

        requireTransition(cmd.effortId, state, EffortStatus.CREATED, EffortStatus.ACTIVE)

        val events = buildList {
            add(EffortStarted(clock.now()))

            // Emit AgentSpawnRequested for each AGENT collaborator
            state.collaborators
                .filter { it.type == CollaboratorType.AGENT }
                .forEach { spec ->
                    val agentId = AgentId.generate()
                    add(
                        AgentSpawnRequested(
                            agentId = agentId,
                            agentTypeId = spec.agentTypeId!!,
                            collaboratorName = spec.name,
                            // DECISION: Generate tokens inline using UUID until TokenGenerator
                            // is available in Phase 7. TokenGenerator uses UUID internally.
                            gatewayToken = UUID.randomUUID().toString(),
                            agentToken = UUID.randomUUID().toString(),
                            requestedAt = clock.now(),
                        )
                    )
                }
        }

        repo.append(
            StreamName.Effort(cmd.effortId),
            events,
            ExpectedVersion.Exact(version),
        )

        logger.info("Effort started: {} ({} agents spawning)", cmd.effortId, events.size - 1)
    }

    /**
     * Pause an active Effort. Emits [EffortPaused].
     */
    override suspend fun handle(cmd: PauseEffort) {
        val (state, version) = maybeLoadEffort(cmd.effortId) ?: throw DomainException(DomainError.EffortNotFound(cmd.effortId))

        requireTransition(cmd.effortId, state, EffortStatus.ACTIVE, EffortStatus.PAUSED)

        repo.append(
            StreamName.Effort(cmd.effortId),
            listOf(EffortPaused(clock.now())),
            ExpectedVersion.Exact(version),
        )

        logger.info("Effort paused: {}", cmd.effortId)
    }

    /**
     * Resume a paused Effort. Emits [EffortResumed].
     */
    override suspend fun handle(cmd: ResumeEffort) {
        val (state, version) = maybeLoadEffort(cmd.effortId) ?: throw DomainException(DomainError.EffortNotFound(cmd.effortId))

        requireTransition(cmd.effortId, state, EffortStatus.PAUSED, EffortStatus.ACTIVE)

        repo.append(
            StreamName.Effort(cmd.effortId),
            listOf(EffortResumed(clock.now())),
            ExpectedVersion.Exact(version),
        )

        logger.info("Effort resumed: {}", cmd.effortId)
    }

    /**
     * Close an Effort. Emits [EffortClosed]. Can close from ACTIVE or PAUSED.
     */
    override suspend fun handle(cmd: CloseEffort) {
        val (state, version) = maybeLoadEffort(cmd.effortId) ?: throw DomainException(DomainError.EffortNotFound(cmd.effortId))

        if (state.status == EffortStatus.CLOSED) {
            throw DomainException(DomainError.EffortAlreadyClosed(cmd.effortId))
        }
        if (state.status == EffortStatus.CREATED) {
            throw DomainException(
                DomainError.InvalidEffortTransition(cmd.effortId, state.status, EffortStatus.CLOSED)
            )
        }

        repo.append(
            StreamName.Effort(cmd.effortId),
            listOf(EffortClosed(clock.now())),
            ExpectedVersion.Exact(version),
        )

        logger.info("Effort closed: {}", cmd.effortId)
    }

    // ---- helpers ----

    private suspend fun maybeLoadEffort(effortId: EffortId): Pair<EffortState, Long>? {
        return repo.maybeLoad(
            EffortEvent::class,
            StreamName.Effort(effortId),
            EffortState::start,
            EffortState::apply
        )
    }

    private fun requireTransition(effortId: EffortId, state: EffortState, requiredFrom: EffortStatus, to: EffortStatus) {
        if (state.status != requiredFrom) {
            throw DomainException(
                DomainError.InvalidEffortTransition(effortId, state.status, to)
            )
        }
    }
}

/**
 * Re-export from error package for convenience within aggregates.
 */
private typealias DomainException = com.bigboote.coordinator.api.error.DomainException
