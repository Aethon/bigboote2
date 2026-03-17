package com.bigboote.domain.events

import com.bigboote.domain.values.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface DocumentEvent {

    @Serializable
    @SerialName("DocumentCreated")
    data class DocumentCreated(
        val documentId: DocumentId,
        val effortId: EffortId,
        val name: String,
        val mimeType: String,
        val s3Key: String,
        val createdBy: CollaboratorName,
        val createdAt: Instant,
    ) : DocumentEvent

    @Serializable
    @SerialName("DocumentUpdated")
    data class DocumentUpdated(
        val documentId: DocumentId,
        val effortId: EffortId,
        val s3Key: String,
        val updatedBy: CollaboratorName,
        val updatedAt: Instant,
    ) : DocumentEvent

    @Serializable
    @SerialName("DocumentDeleted")
    data class DocumentDeleted(
        val documentId: DocumentId,
        val effortId: EffortId,
        val deletedBy: CollaboratorName,
        val deletedAt: Instant,
    ) : DocumentEvent
}
