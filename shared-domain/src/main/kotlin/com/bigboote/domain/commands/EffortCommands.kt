package com.bigboote.domain.commands

import com.bigboote.domain.values.*

sealed interface EffortCommand {

    data class CreateEffort(
        val effortId: EffortId,
        val name: String,
        val goal: String,
        val collaborators: List<CollaboratorSpec>,
        val leadName: CollaboratorName,
    ) : EffortCommand

    data class StartEffort(val effortId: EffortId) : EffortCommand

    data class PauseEffort(val effortId: EffortId) : EffortCommand

    data class ResumeEffort(val effortId: EffortId) : EffortCommand

    data class CloseEffort(val effortId: EffortId) : EffortCommand
}
