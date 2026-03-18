package com.bigboote.coordinator.reactors

import com.bigboote.coordinator.aggregates.AggregateRepository
import com.bigboote.coordinator.system.SystemCollaborator
import com.bigboote.domain.aggregates.EffortState
import com.bigboote.domain.events.EffortEvent
import com.bigboote.domain.events.EffortEvent.*
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.EffortId
import com.bigboote.events.eventstore.EventStore
import com.bigboote.events.eventstore.EventSubscription
import com.bigboote.events.streams.StreamNames
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(SystemMessageReactor::class.java)

/**
 * Reacts to Effort lifecycle events and sends `@system` DMs to all collaborators.
 *
 * Uses a persistent subscription on `\$all` (group: `system-message-reactor`).
 * On each lifecycle event the reactor:
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
     * Start the persistent subscription on `\$all`.
     * Called by [ReactorRunner] at coordinator startup.
     */
    fun start() {
        subscription = eventStore.subscribePersistent(
            streamId  = "\$all",
            groupName = "system-message-reactor",
        ) { envelope ->
            when (val event = envelope.data) {
                is EffortStarted  -> handleLifecycle(event.effortId, "started")
                is EffortPaused   -> handleLifecycle(event.effortId, "paused")
                is EffortResumed  -> handleLifecycle(event.effortId, "resumed")
                is EffortClosed   -> handleLifecycle(event.effortId, "closed")
                else              -> Unit  // ignore all other events
            }
        }
        logger.info(
            "SystemMessageReactor started (persistent subscription on \$all, group=system-message-reactor)"
        )
    }

    /**
     * Stop the persistent subscription.
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

        val state = loadEffortState(effortId)
        if (state.effortId.value == "effort:__empty__") {
            logger.warn(
                "SystemMessageReactor: EffortState not found for {} — skipping @system DMs",
                effortId,
            )
            return
        }

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

    private suspend fun loadEffortState(effortId: EffortId): EffortState {
        val (state, _) = repo.load(
            StreamNames.effort(effortId),
            EffortState.EMPTY,
        ) { s, event ->
            if (event is EffortEvent) s.apply(event) else s
        }
        return state
    }
}
