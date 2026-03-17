package com.bigboote.domain.commands

import com.bigboote.domain.values.*

sealed interface DocumentCommand {

    data class CreateDocument(
        val documentId: DocumentId,
        val effortId: EffortId,
        val name: String,
        val mimeType: String,
        val s3Key: String,
        val createdBy: CollaboratorName,
    ) : DocumentCommand

    data class UpdateDocument(
        val documentId: DocumentId,
        val effortId: EffortId,
        val s3Key: String,
        val updatedBy: CollaboratorName,
    ) : DocumentCommand

    data class DeleteDocument(
        val documentId: DocumentId,
        val effortId: EffortId,
        val deletedBy: CollaboratorName,
    ) : DocumentCommand
}
