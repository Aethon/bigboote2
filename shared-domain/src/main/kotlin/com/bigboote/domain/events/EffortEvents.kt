package com.bigboote.domain.events

import com.bigboote.domain.values.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Events for the Effort lifecycle stream: `/effort:{id}`
 *
 * The [EffortId] is no longer carried in each event payload — it is inherent in the
 * [StreamName.Effort] stream name. Retrieve it via [StreamName.Effort.id] from the
 * enclosing [com.bigboote.events.eventstore.EventEnvelope.streamName].
 *
 * See Architecture doc Change Document v1.0 Section 5.1.
 */
@Serializable
sealed interface EffortEvent :
    Event {

    @Serializable
    @SerialName("EffortCreated")
    data class EffortCreated(
        val name: String,
        val goal: String,
        val collaborators: List<CollaboratorSpec>,
        val leadName: CollaboratorName,
        val createdAt: Instant,
    ) : EffortEvent

    @Serializable
    @SerialName("EffortStarted")
    data class EffortStarted(
        val occurredAt: Instant,
    ) : EffortEvent

    @Serializable
    @SerialName("EffortPaused")
    data class EffortPaused(
        val occurredAt: Instant,
    ) : EffortEvent

    @Serializable
    @SerialName("EffortResumed")
    data class EffortResumed(
        val occurredAt: Instant,
    ) : EffortEvent

    @Serializable
    @SerialName("EffortClosed")
    data class EffortClosed(
        val occurredAt: Instant,
    ) : EffortEvent

    @Serializable
    @SerialName("AgentSpawnRequested")
    data class AgentSpawnRequested(
        val agentId: AgentId,
        val agentTypeId: AgentTypeId,
        val collaboratorName: CollaboratorName,
        val gatewayToken: String,
        val agentToken: String,
        val requestedAt: Instant,
    ) : EffortEvent
}

/**
 * Safely cast an untyped [StreamName] to [StreamName.Effort].
 * Use this in [EventStore.subscribeToAll] handlers after a type-check on `envelope.data`.
 */
fun StreamName<*>.asEffortStream(): StreamName.Effort =
    this as? StreamName.Effort
        ?: error("Expected StreamName.Effort but got ${this::class.simpleName} for path '$path'")
