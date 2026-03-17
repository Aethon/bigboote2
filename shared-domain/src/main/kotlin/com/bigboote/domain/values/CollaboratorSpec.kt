package com.bigboote.domain.values

import kotlinx.serialization.Serializable

@Serializable
data class CollaboratorSpec(
    val name: CollaboratorName,
    val type: CollaboratorType,
    val agentTypeId: AgentTypeId? = null,
    val isLead: Boolean = false,
)
