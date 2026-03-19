package com.bigboote.domain.aggregates

import com.bigboote.domain.events.DocumentEvent
import com.bigboote.domain.events.DocumentEvent.*
import com.bigboote.domain.events.asEffortStream
import com.bigboote.domain.values.*
import kotlinx.datetime.Instant

data class DocumentRecord(
    val documentId: DocumentId,
    val name: String,
    val mimeType: String,
    val s3Key: String,
    val createdBy: CollaboratorName,
    val createdAt: Instant,
    val deleted: Boolean = false,
)

data class DocumentStoreState(
    val effortId: EffortId,
    val documents: Map<DocumentId, DocumentRecord>,
) : NoContextStreamState<DocumentEvent, DocumentStoreState>() {
    override fun apply(event: DocumentEvent): DocumentStoreState = when (event) {
        is DocumentCreated -> copy(
            documents = documents + (event.documentId to DocumentRecord(
                documentId = event.documentId,
                name = event.name,
                mimeType = event.mimeType,
                s3Key = event.s3Key,
                createdBy = event.createdBy,
                createdAt = event.createdAt,
            ))
        )

        is DocumentUpdated -> {
            val existing = documents[event.documentId]
                ?: return this // DECISION: ignore update for unknown doc
            copy(documents = documents + (event.documentId to existing.copy(s3Key = event.s3Key)))
        }

        is DocumentDeleted -> {
            val existing = documents[event.documentId]
                ?: return this // DECISION: ignore delete for unknown doc
            copy(documents = documents + (event.documentId to existing.copy(deleted = true)))
        }
    }

    companion object : StreamStateStarter<DocumentEvent, DocumentStoreState> {
        override fun start(entry: EventLogEntry<DocumentEvent>): DocumentStoreState {
            val event = entry.event as? DocumentCreated
                ?: throw IllegalArgumentException("Must start with DocumentCreated event")

            return DocumentStoreState(
                effortId = entry.streamName.asEffortStream().id,
                documents = mapOf(
                    event.documentId to DocumentRecord(
                        documentId = event.documentId,
                        name = event.name,
                        mimeType = event.mimeType,
                        s3Key = event.s3Key,
                        createdBy = event.createdBy,
                        createdAt = event.createdAt,
                    )
                )
            )
        }
    }
}
