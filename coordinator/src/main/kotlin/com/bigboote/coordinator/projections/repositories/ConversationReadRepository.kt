package com.bigboote.coordinator.projections.repositories

import com.bigboote.coordinator.projections.db.ConversationTable
import com.bigboote.coordinator.projections.db.MessageTable
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.EffortId
import com.bigboote.infra.db.dbQuery
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ConversationReadRepository::class.java)

/**
 * Read-only query interface for the `conversations` and `messages` Postgres tables.
 *
 * Returns denormalised read models populated by [ConversationProjection].
 * Never reads from KurrentDB — all queries go to the Postgres read model.
 *
 * See Architecture doc Section 8 and API Design doc Section 3.4.
 */
class ConversationReadRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // ------------------------------------------------------------------ query API

    /**
     * List all conversations for a given [effortId].
     * Returns conversation summaries ordered by [ConversationTable.createdAt] ascending.
     */
    suspend fun list(effortId: EffortId): List<ConversationRow> = dbQuery {
        ConversationTable
            .selectAll()
            .where { ConversationTable.effortId eq effortId.value }
            .orderBy(ConversationTable.createdAt, SortOrder.ASC)
            .map { it.toConversationRow() }
    }

    /**
     * Get a single conversation by [effortId] and [convId]. Returns null if not found.
     */
    suspend fun get(effortId: EffortId, convId: String): ConversationRow? = dbQuery {
        ConversationTable
            .selectAll()
            .where {
                (ConversationTable.effortId eq effortId.value) and
                (ConversationTable.convId eq convId)
            }
            .singleOrNull()
            ?.toConversationRow()
    }

    /**
     * Get a paginated list of messages for a conversation.
     *
     * Results are ordered by [MessageTable.postedAt] ascending.
     *
     * @param effortId  the effort that owns the conversation
     * @param convId    the full convId string (e.g. "conv:#review")
     * @param from      zero-based offset for pagination (default 0)
     * @param limit     maximum number of messages to return (default 50, max 200)
     */
    suspend fun getMessages(
        effortId: EffortId,
        convId: String,
        from: Int = 0,
        limit: Int = 50,
    ): List<MessageRow> = dbQuery {
        val clampedLimit = limit.coerceIn(1, 200)
        MessageTable
            .selectAll()
            .where {
                (MessageTable.effortId eq effortId.value) and
                (MessageTable.convId eq convId)
            }
            .orderBy(MessageTable.postedAt, SortOrder.ASC)
            .limit(clampedLimit).offset(from.toLong())
            .map { it.toMessageRow() }
    }

    /**
     * Return the [CollaboratorName] list for a conversation.
     * Used by [com.bigboote.coordinator.reactors.MessageDeliveryReactor] to resolve
     * the set of recipients for a [com.bigboote.domain.events.ConversationEvent.MessagePosted].
     *
     * Returns an empty list if the conversation is not yet in the read model
     * (projection lag); the reactor will simply deliver to no-one in that case.
     */
    suspend fun getMembersForConv(effortId: EffortId, convId: String): List<CollaboratorName> = dbQuery {
        ConversationTable
            .selectAll()
            .where {
                (ConversationTable.effortId eq effortId.value) and
                (ConversationTable.convId eq convId)
            }
            .singleOrNull()
            ?.let { row ->
                val membersJson = row[ConversationTable.members]
                try {
                    json.decodeFromString<List<String>>(membersJson)
                        .mapNotNull { str ->
                            runCatching { CollaboratorName.from(str) }.getOrNull()
                        }
                } catch (e: Exception) {
                    logger.warn(
                        "Failed to deserialize members for conv {} in effort {}: {}",
                        convId, effortId, e.message,
                    )
                    emptyList()
                }
            }
            ?: emptyList()
    }

    // ------------------------------------------------------------------ helpers

    private fun ResultRow.toConversationRow(): ConversationRow {
        val membersJson = this[ConversationTable.members]
        val members: List<String> = try {
            json.decodeFromString(membersJson)
        } catch (e: Exception) {
            logger.warn(
                "Failed to deserialize members JSON for conv {} in effort {}: {}",
                this[ConversationTable.convId], this[ConversationTable.effortId], e.message
            )
            emptyList()
        }
        return ConversationRow(
            effortId  = EffortId(this[ConversationTable.effortId]),
            convId    = this[ConversationTable.convId],
            convName  = this[ConversationTable.convName],
            members   = members,
            createdAt = this[ConversationTable.createdAt],
        )
    }

    private fun ResultRow.toMessageRow(): MessageRow = MessageRow(
        messageId = this[MessageTable.messageId],
        convId    = this[MessageTable.convId],
        effortId  = EffortId(this[MessageTable.effortId]),
        fromName  = this[MessageTable.fromName],
        body      = this[MessageTable.body],
        postedAt  = this[MessageTable.postedAt],
    )
}

/**
 * In-memory read model row for a Conversation.
 */
data class ConversationRow(
    val effortId:  EffortId,
    val convId:    String,
    val convName:  String,
    val members:   List<String>,   // stored as "@name" or "#name" strings
    val createdAt: Instant,
)

/**
 * In-memory read model row for a Message.
 */
data class MessageRow(
    val messageId: String,
    val convId:    String,
    val effortId:  EffortId,
    val fromName:  String,   // stored as "@name" or "#name"
    val body:      String,
    val postedAt:  Instant,
)
