package com.bigboote.coordinator.projections.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed table definition for the Document read model.
 *
 * Populated by [com.bigboote.coordinator.projections.DocumentListProjection]
 * from KurrentDB document events (DocumentCreated / DocumentUpdated / DocumentDeleted).
 *
 * DECISION: `document_id` is the primary key because document IDs (e.g. "doc:V1St…")
 * are globally unique (NanoId). The `effort_id` column is a logical FK to [EffortTable]
 * but is NOT a DB-level FK to avoid cross-table DDL ordering dependencies and to keep
 * the read model independent of the write model.
 *
 * DECISION: `deleted` is a soft-delete flag; rows are never physically removed so that
 * the projection remains idempotent on replay.
 *
 * See Architecture doc Section 14 and PHASE_14_COMPLETE_EXTRACTION.txt.
 */
object DocumentTable : Table("documents") {
    val documentId = varchar("document_id", 64).uniqueIndex()
    val effortId   = varchar("effort_id", 64)
    val name       = varchar("name", 256)
    val mimeType   = varchar("mime_type", 64)
    val s3Key      = varchar("s3_key", 512)
    val createdAt  = timestamp("created_at")
    val createdBy  = varchar("created_by", 64)
    val deleted    = bool("deleted").default(false)

    override val primaryKey = PrimaryKey(documentId)
}
