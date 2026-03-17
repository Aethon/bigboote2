package com.bigboote.coordinator.projections.db

import com.bigboote.domain.aggregates.EffortStatus
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed table definition for the Effort read model.
 *
 * Populated by [EffortSummaryProjection] from EffortEvent catch-up subscription.
 * See Architecture doc Section 8.2.
 *
 * DECISION: Added `collaborators` TEXT column (JSON) to store the collaborator list
 * from EffortCreated. The Architecture doc's EffortTable schema does not include it,
 * but the GET /api/v1/efforts/{id} response requires collaborator data. Storing as
 * JSON avoids a separate join table and matches the read-model denormalization philosophy.
 */
object EffortTable : Table("efforts") {
    val effortId = varchar("effort_id", 64)
    val name = varchar("name", 256)
    val goal = text("goal")
    val status = enumerationByName<EffortStatus>("status", 16)
    val leadName = varchar("lead_name", 64)
    val collaborators = text("collaborators").default("[]") // JSON array of CollaboratorSpec
    val createdAt = timestamp("created_at")
    val startedAt = timestamp("started_at").nullable()
    val closedAt = timestamp("closed_at").nullable()

    override val primaryKey = PrimaryKey(effortId)
}
