package com.bigboote.coordinator.projections.repositories

import com.bigboote.coordinator.projections.db.DocumentTable
import com.bigboote.domain.values.DocumentId
import com.bigboote.domain.values.EffortId
import com.bigboote.infra.db.dbQuery
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(DocumentReadRepository::class.java)

/**
 * Read-only query interface for the `documents` Postgres table.
 *
 * Returns denormalised read models populated by
 * [com.bigboote.coordinator.projections.DocumentListProjection].
 * Never reads from KurrentDB — all queries go to the Postgres read model.
 *
 * Soft-deleted documents (where [DocumentTable.deleted] = true) are excluded
 * from all query results.
 *
 * See Architecture doc Section 14 and API Design doc Section 3.5.
 */
class DocumentReadRepository {

    // ------------------------------------------------------------------ query API

    /**
     * List all non-deleted documents for [effortId],
     * ordered by [DocumentTable.createdAt] ascending.
     */
    suspend fun list(effortId: EffortId): List<DocumentRow> = dbQuery {
        DocumentTable
            .selectAll()
            .where {
                (DocumentTable.effortId eq effortId.value) and
                (DocumentTable.deleted eq false)
            }
            .orderBy(DocumentTable.createdAt, SortOrder.ASC)
            .map { it.toDocumentRow() }
    }

    /**
     * Get a single non-deleted document by [effortId] and [documentId].
     * Returns null if the document does not exist or has been soft-deleted.
     */
    suspend fun get(effortId: EffortId, documentId: DocumentId): DocumentRow? = dbQuery {
        DocumentTable
            .selectAll()
            .where {
                (DocumentTable.documentId eq documentId.value) and
                (DocumentTable.effortId eq effortId.value) and
                (DocumentTable.deleted eq false)
            }
            .singleOrNull()
            ?.toDocumentRow()
    }

    // ------------------------------------------------------------------ helpers

    private fun ResultRow.toDocumentRow(): DocumentRow = DocumentRow(
        documentId = DocumentId(this[DocumentTable.documentId]),
        effortId   = EffortId(this[DocumentTable.effortId]),
        name       = this[DocumentTable.name],
        mimeType   = this[DocumentTable.mimeType],
        s3Key      = this[DocumentTable.s3Key],
        createdAt  = this[DocumentTable.createdAt],
        createdBy  = this[DocumentTable.createdBy],
    )
}

/**
 * In-memory read model row for a Document.
 * Represents a single non-deleted document in the [DocumentTable].
 */
data class DocumentRow(
    val documentId: DocumentId,
    val effortId:   EffortId,
    val name:       String,
    val mimeType:   String,
    val s3Key:      String,
    val createdAt:  Instant,
    val createdBy:  String,
)
