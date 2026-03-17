package com.bigboote.domain.events

import com.bigboote.domain.values.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface EffortEvent {
    val effortId: EffortId

    @Serializable
    @SerialName("EffortCreated")
    data class EffortCreated(
        override val effortId: EffortId,
        val name: String,
        val goal: String,
        val collaborators: List<CollaboratorSpec>,
        val leadName: CollaboratorName,
        val createdAt: Instant,
    ) : EffortEvent

    @Serializable
    @SerialName("EffortStarted")
    data class EffortStarted(
        override val effortId: EffortId,
        val occurredAt: Instant,
    ) : EffortEvent

    @Serializable
    @SerialName("EffortPaused")
    data class EffortPaused(
        override val effortId: EffortId,
        val occurredAt: Instant,
    ) : EffortEvent

    @Serializable
    @SerialName("EffortResumed")
    data class EffortResumed(
        override val effortId: EffortId,
        val occurredAt: Instant,
    ) : EffortEvent

    @Serializable
    @SerialName("EffortClosed")
    data class EffortClosed(
        override val effortId: EffortId,
        val occurredAt: Instant,
    ) : EffortEvent

    @Serializable
    @SerialName("AgentSpawnRequested")
    data class AgentSpawnRequested(
        val agentId: AgentId,
        override val effortId: EffortId,
        val agentTypeId: AgentTypeId,
        val collaboratorName: CollaboratorName,
        val gatewayToken: String,
        val agentToken: String,
        val requestedAt: Instant,
    ) : EffortEvent
}
