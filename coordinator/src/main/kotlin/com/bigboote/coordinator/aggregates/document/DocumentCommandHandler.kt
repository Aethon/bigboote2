package com.bigboote.coordinator.aggregates.document

import com.bigboote.coordinator.aggregates.AggregateRepository
import com.bigboote.coordinator.api.error.DomainException
import com.bigboote.coordinator.storage.S3DocumentStorage
import com.bigboote.domain.aggregates.DocumentStoreState
import com.bigboote.domain.commands.DocumentCommand.*
import com.bigboote.domain.errors.DomainError
import com.bigboote.domain.events.DocumentEvent.*
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.StreamName
import com.bigboote.events.eventstore.ExpectedVersion
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(DocumentCommandHandler::class.java)

/**
 * Handles all Document aggregate commands by loading [DocumentStoreState] from
 * KurrentDB, performing domain validation, uploading content to S3, and appending
 * the corresponding domain event to the docs stream.
 *
 * All three commands share the same stream: [StreamName.Docs].
 *
 * S3 upload happens **before** appending the event to ensure content is durable
 * before any projection or reactor observes the event. The s3Key is derived via
 * [S3DocumentStorage.computeKey] and is therefore deterministic given the same inputs.
 *
 * See Architecture doc Section 14 and API Design doc Section 3.5.
 */
class DocumentCommandHandler(
    private val repo: AggregateRepository,
    private val clock: Clock,
    private val s3: S3DocumentStorage,
) {

    /**
     * Create a new document.
     *
     * The [content] parameter carries the UTF-8 document body; the command's
     * [CreateDocument.s3Key] field is ignored — the handler recomputes it
     * deterministically via [S3DocumentStorage.computeKey].
     *
     * Emits [DocumentCreated] on the effort docs stream.
     */
    suspend fun handle(cmd: CreateDocument, content: String) {
        val (state, version) = loadDocumentStore(cmd.effortId)

        // DocumentId is generated fresh (NanoId) by the route handler, so collisions are
        // astronomically unlikely. No dedicated DocumentAlreadyExists error in Phase 14.

        val s3Key = s3.computeKey(cmd.effortId, cmd.documentId, cmd.name)

        s3.put(s3Key, content, cmd.mimeType)

        val event = DocumentCreated(
            documentId = cmd.documentId,
            name       = cmd.name,
            mimeType   = cmd.mimeType,
            s3Key      = s3Key,
            createdBy  = cmd.createdBy,
            createdAt  = clock.now(),
        )

        repo.append(
            StreamName.Docs(cmd.effortId),
            listOf(event),
            if (version < 0) ExpectedVersion.NoStream else ExpectedVersion.Exact(version),
        )

        logger.info("Document created: {} in effort {}", cmd.documentId, cmd.effortId)
    }

    /**
     * Update an existing document's content.
     *
     * Uploads the new content to S3 under a fresh key (same key formula but with
     * the same docId and name — content is overwritten), then appends [DocumentUpdated].
     *
     * Throws [DocumentNotFound] if the document does not exist or has been deleted.
     */
    suspend fun handle(cmd: UpdateDocument, content: String) {
        val (state, version) = loadDocumentStore(cmd.effortId)

        val record = state.documents[cmd.documentId]
            ?: throw DomainException(DomainError.DocumentNotFound(cmd.documentId))

        if (record.deleted) throw DomainException(DomainError.DocumentNotFound(cmd.documentId))

        val s3Key = s3.computeKey(cmd.effortId, cmd.documentId, record.name)

        s3.put(s3Key, content, record.mimeType)

        val event = DocumentUpdated(
            documentId = cmd.documentId,
            s3Key      = s3Key,
            updatedBy  = cmd.updatedBy,
            updatedAt  = clock.now(),
        )

        repo.append(
            StreamName.Docs(cmd.effortId),
            listOf(event),
            ExpectedVersion.Exact(version),
        )

        logger.info("Document updated: {} in effort {}", cmd.documentId, cmd.effortId)
    }

    /**
     * Soft-delete a document.
     *
     * Appends [DocumentDeleted]; S3 content is retained (physical deletion is out of scope
     * for Phase 14 and can be handled by a lifecycle policy or a future cleanup job).
     *
     * Throws [DocumentNotFound] if the document does not exist or is already deleted.
     */
    suspend fun handle(cmd: DeleteDocument) {
        val (state, version) = loadDocumentStore(cmd.effortId)

        val record = state.documents[cmd.documentId]
            ?: throw DomainException(DomainError.DocumentNotFound(cmd.documentId))

        if (record.deleted) throw DomainException(DomainError.DocumentNotFound(cmd.documentId))

        val event = DocumentDeleted(
            documentId = cmd.documentId,
            deletedBy  = cmd.deletedBy,
            deletedAt  = clock.now(),
        )

        repo.append(
            StreamName.Docs(cmd.effortId),
            listOf(event),
            ExpectedVersion.Exact(version),
        )

        logger.info("Document deleted: {} in effort {}", cmd.documentId, cmd.effortId)
    }

    // ---- private helpers ----

    private suspend fun loadDocumentStore(
        effortId: EffortId,
    ): Pair<DocumentStoreState, Long> =
        repo.load(
            StreamName.Docs(effortId),
            DocumentStoreState.empty(effortId),
        ) { s, event ->
            if (event is com.bigboote.domain.events.DocumentEvent) s.apply(event) else s
        }
}
