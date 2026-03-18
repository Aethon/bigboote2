package com.bigboote.coordinator.projections.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed table definition for the Message read model.
 *
 * Populated by [ConversationProjection] from [MessagePosted] events.
 *
 * DECISION: PK on `messageId` (globally unique NanoId with "msg:" prefix).
 * The `(effortId, convId)` pair is included for efficient per-conversation
 * message queries ordered by `postedAt`.
 *
 * See Architecture doc Section 8.2.
 */
object MessageTable : Table("messages") {
    val messageId = varchar("message_id", 64)
    val convId    = varchar("conv_id", 128)
    val effortId  = varchar("effort_id", 64)
    val fromName  = varchar("from_name", 64)
    val body      = text("body")
    val postedAt  = timestamp("posted_at")

    override val primaryKey = PrimaryKey(messageId)
}
