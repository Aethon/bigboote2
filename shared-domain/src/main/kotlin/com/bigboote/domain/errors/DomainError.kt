package com.bigboote.domain.errors

import com.bigboote.domain.aggregates.EffortStatus
import com.bigboote.domain.values.DocumentId
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.AgentTypeId

sealed class DomainError(val message: String) {

    // Effort errors
    data class EffortNotFound(val effortId: EffortId) :
        DomainError("Effort not found: ${effortId.value}")

    data class InvalidEffortTransition(val effortId: EffortId, val from: EffortStatus, val to: EffortStatus) :
        DomainError("Invalid effort transition from $from to $to for ${effortId.value}")

    data class EffortAlreadyClosed(val effortId: EffortId) :
        DomainError("Effort already closed: ${effortId.value}")

    // AgentType errors
    data class AgentTypeNotFound(val agentTypeId: AgentTypeId) :
        DomainError("Agent type not found: ${agentTypeId.value}")

    data class InvalidAgentTypeSlug(val slug: String) :
        DomainError("Invalid agent type slug: '$slug'")

    // Conversation errors
    data class ConversationNotFound(val convId: String) :
        DomainError("Conversation not found: $convId")

    // Document errors
    data class DocumentNotFound(val documentId: DocumentId) :
        DomainError("Document not found: ${documentId.value}")
}
