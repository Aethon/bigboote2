package com.bigboote.coordinator.projections.repositories

import com.bigboote.coordinator.projections.db.EffortTable
import com.bigboote.domain.aggregates.EffortStatus
import com.bigboote.domain.values.CollaboratorSpec
import com.bigboote.domain.values.EffortId
import com.bigboote.infra.db.dbQuery
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(EffortReadRepositoryImpl::class.java)

/**
 * Read-only query interface for the `efforts` Postgres table.
 *
 * Extracted as an interface so that route-layer tests can mock it without
 * needing a real Postgres connection. The sole production implementation
 * is [EffortReadRepositoryImpl].
 *
 * See Architecture doc Section 8.
 */
interface EffortReadRepository {
    suspend fun list(status: EffortStatus? = null): List<EffortRow>
    suspend fun get(effortId: EffortId): EffortRow?
}

/**
 * Production implementation of [EffortReadRepository].
 *
 * Returns denormalised read models populated by [EffortSummaryProjection].
 * Never reads from KurrentDB — all queries go to the Postgres read model.
 *
 * See Architecture doc Section 8.
 */
class EffortReadRepositoryImpl : EffortReadRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // ------------------------------------------------------------------ query API

    /**
     * List all Efforts, optionally filtered by [status].
     */
    override suspend fun list(status: EffortStatus?): List<EffortRow> = dbQuery {
        val query = EffortTable.selectAll()
        if (status != null) {
            query.andWhere { EffortTable.status eq status }
        }
        query.map { it.toEffortRow() }
    }

    /**
     * Get a single Effort by ID. Returns null if not found.
     */
    override suspend fun get(effortId: EffortId): EffortRow? = dbQuery {
        EffortTable
            .selectAll()
            .where { EffortTable.effortId eq effortId.value }
            .singleOrNull()
            ?.toEffortRow()
    }

    // ------------------------------------------------------------------ helpers

    private fun ResultRow.toEffortRow(): EffortRow {
        val collaboratorsJson = this[EffortTable.collaborators]
        val collaborators = try {
            json.decodeFromString<List<CollaboratorSpec>>(collaboratorsJson)
        } catch (e: Exception) {
            logger.warn("Failed to deserialize collaborators JSON for effort {}: {}", this[EffortTable.effortId], e.message)
            emptyList()
        }

        return EffortRow(
            effortId = EffortId(this[EffortTable.effortId]),
            name = this[EffortTable.name],
            goal = this[EffortTable.goal],
            status = this[EffortTable.status],
            leadName = this[EffortTable.leadName],
            collaborators = collaborators,
            createdAt = this[EffortTable.createdAt],
            startedAt = this[EffortTable.startedAt],
            closedAt = this[EffortTable.closedAt],
        )
    }
}

/**
 * In-memory read model row for an Effort.
 * Distinct from the domain [com.bigboote.domain.aggregates.EffortState] —
 * this is a denormalised, query-optimised view.
 */
data class EffortRow(
    val effortId: EffortId,
    val name: String,
    val goal: String,
    val status: EffortStatus,
    val leadName: String,            // stored as "@name" — route strips prefix for API response
    val collaborators: List<CollaboratorSpec>,
    val createdAt: Instant,
    val startedAt: Instant?,
    val closedAt: Instant?,
)
