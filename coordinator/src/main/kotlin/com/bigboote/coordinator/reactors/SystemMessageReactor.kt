package com.bigboote.coordinator.reactors

import com.bigboote.coordinator.aggregates.AggregateRepository
import com.bigboote.coordinator.system.SystemCollaborator
import com.bigboote.domain.aggregates.EffortState
import com.bigboote.domain.events.EffortEvent
import com.bigboote.domain.events.EffortEvent.*
import com.bigboote.domain.events.asEffortStream
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.StreamName
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.eventstore.EventSubscription
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(SystemMessageReactor::class.java)

/**
 * Reacts to Effort lifecycle events and sends `@system` DMs to all collaborators.
 *
 * Uses [EventStore.subscribeToAll] for at-least-once delivery across coordinator
 * restarts. On each lifecycle event the reactor extracts the [EffortId] from
 * [envelope.streamName][com.bigboote.events.eventstore.EventEnvelope.streamName]
 * via [asEffortStream], then:
 * 1. Loads the current [EffortState] from KurrentDB to obtain the collaborator list.
 * 2. Sends a DM from `@system` to each non-system collaborator via [SystemCollaborator].
 *
 * **Events handled:**
 * - [EffortStarted]  → "Effort '<name>' has started."
 * - [EffortPaused]   → "Effort '<name>' has been paused."
 * - [EffortResumed]  → "Effort '<name>' has been resumed."
 * - [EffortClosed]   → "Effort '<name>' has closed."
 *
 * DM conversations are auto-created on-demand by [ConversationCommandHandler.handle]
 * so there is no explicit channel-creation step.
 *
 * DM failures are logged as warnings and do not crash the reactor.
 *
 * See Architecture doc Section 15.
 */
class SystemMessageReactor(
    private val eventStore: EventStore,
    private val repo: AggregateRepository,
    private val systemCollaborator: SystemCollaborator,
) {
    private var subscription: EventSubscription? = null

    /**
     * Start the \$all catch-up subscription.
     * Called by [ReactorRunner] at coordinator startup.
     */
    fun start() {
        subscription = eventStore.subscribeToAll { envelope ->
            val effortId by lazy { envelope.streamName.asEffortStream().id }
            when (envelope.data) {
                is EffortStarted  -> handleLifecycle(effortId, "started")
                is EffortPaused   -> handleLifecycle(effortId, "paused")
                is EffortResumed  -> handleLifecycle(effortId, "resumed")
                is EffortClosed   -> handleLifecycle(effortId, "closed")
                else              -> Unit  // ignore all other events
            }
        }
        logger.info("SystemMessageReactor started (\$all catch-up subscription)")
    }

    /**
     * Stop the subscription.
     * Called by [ReactorRunner] on coordinator shutdown.
     */
    fun stop() {
        subscription?.stop()
        subscription = null
        logger.info("SystemMessageReactor stopped")
    }

    // ---- private helpers ----

    private suspend fun handleLifecycle(effortId: EffortId, verb: String) {
        logger.debug("SystemMessageReactor: effort {} {}", effortId, verb)

        val state = loadEffortState(effortId) ?: throw IllegalStateException("Effort not found: $effortId")

        val body = "Effort '${state.name}' has $verb."

        val recipients = state.collaborators
            .map { it.name }
            .filter { it != systemCollaborator.name }  // don't DM @system itself

        logger.info(
            "SystemMessageReactor: sending @system DM to {} collaborator(s) in effort {} ({})",
            recipients.size, effortId, verb,
        )

        for (recipient in recipients) {
            systemCollaborator.sendDm(effortId, recipient, body)
        }
    }

    private suspend fun loadEffortState(effortId: EffortId): EffortState? {
        return repo.maybeLoad(
            EffortEvent::class,
            StreamName.Effort(effortId),
            EffortState::start,
            EffortState::apply
        )?.first
    }
}
