package com.bigboote.coordinator.projections.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed table definition for the Conversation read model.
 *
 * Populated by [ConversationProjection] from KurrentDB conversation events.
 *
 * DECISION: Composite PK (effortId, convId) because conversation IDs (e.g. "#review")
 * are scoped to an Effort — the same channel name can exist in multiple efforts.
 *
 * DECISION: `members` stored as a JSON text array to avoid a separate join table.
 * The read model denormalizes member lists for efficient list and detail queries.
 *
 * See Architecture doc Section 8.2.
 */
object ConversationTable : Table("conversations") {
    val effortId  = varchar("effort_id", 64)
    val convId    = varchar("conv_id", 128)
    val convName  = varchar("conv_name", 128)
    val members   = text("members").default("[]")    // JSON array of "@name" or "#name" strings
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(effortId, convId)
}
