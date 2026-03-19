package com.bigboote.domain.aggregates

import com.bigboote.domain.events.EffortEvent
import com.bigboote.domain.events.EffortEvent.*
import com.bigboote.domain.values.*
import kotlinx.datetime.Instant

data class EffortState(
    val name: String,
    val goal: String,
    val status: EffortStatus,
    val collaborators: List<CollaboratorSpec>,
    val leadName: CollaboratorName,
    val version: Long,
) {
    fun apply(event: EffortEvent): EffortState = when (event) {
        is EffortCreated -> EffortState(
            name = event.name,
            goal = event.goal,
            status = EffortStatus.CREATED,
            collaborators = event.collaborators,
            leadName = event.leadName,
            version = 1,
        )
        is EffortStarted -> copy(status = EffortStatus.ACTIVE, version = version + 1)
        is EffortPaused -> copy(status = EffortStatus.PAUSED, version = version + 1)
        is EffortResumed -> copy(status = EffortStatus.ACTIVE, version = version + 1)
        is EffortClosed -> copy(status = EffortStatus.CLOSED, version = version + 1)
        is AgentSpawnRequested -> copy(version = version + 1)
    }

    companion object {
        val EMPTY = EffortState(
            name = "",
            goal = "",
            status = EffortStatus.CREATED,
            collaborators = emptyList(),
            leadName = CollaboratorName.Individual("__empty__"),
            version = 0,
        )
    }
}
