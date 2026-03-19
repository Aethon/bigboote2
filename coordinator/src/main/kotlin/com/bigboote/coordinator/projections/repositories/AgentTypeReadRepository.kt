package com.bigboote.coordinator.projections.repositories

import com.bigboote.coordinator.projections.db.AgentTypeTable
import com.bigboote.domain.values.AgentTypeId
import com.bigboote.infra.db.dbQuery
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AgentTypeReadRepositoryImpl::class.java)

/**
 * Contract for the AgentType read model repository.
 * Extracted as an interface so that route-layer tests can mock it without
 * requiring a live Postgres connection.
 */
interface AgentTypeReadRepository {
    /** List all AgentTypes ordered by creation time ascending. */
    suspend fun list(): List<AgentTypeRow>
    /** Get a single AgentType by ID. Returns null if not found. */
    suspend fun get(agentTypeId: AgentTypeId): AgentTypeRow?
}

/**
 * Read-only query interface for the `agent_types` Postgres table.
 *
 * Returns denormalised read models populated by [AgentTypeSummaryProjection].
 * Never reads from KurrentDB — all queries go to the Postgres read model.
 *
 * See Architecture doc Section 8.
 */
class AgentTypeReadRepositoryImpl : AgentTypeReadRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // ------------------------------------------------------------------ query API

    /**
     * List all AgentTypes ordered by creation time ascending.
     */
    override suspend fun list(): List<AgentTypeRow> = dbQuery {
        AgentTypeTable
            .selectAll()
            .orderBy(AgentTypeTable.createdAt)
            .map { it.toRow() }
    }

    /**
     * Get a single AgentType by ID. Returns null if not found.
     */
    override suspend fun get(agentTypeId: AgentTypeId): AgentTypeRow? = dbQuery {
        AgentTypeTable
            .selectAll()
            .where { AgentTypeTable.agentTypeId eq agentTypeId.value }
            .singleOrNull()
            ?.toRow()
    }

    // ------------------------------------------------------------------ helpers

    private fun ResultRow.toRow(): AgentTypeRow {
        val toolsJson = this[AgentTypeTable.tools]
        val tools = try {
            json.decodeFromString<List<String>>(toolsJson)
        } catch (e: Exception) {
            logger.warn(
                "Failed to deserialize tools JSON for agenttype {}: {}",
                this[AgentTypeTable.agentTypeId], e.message
            )
            emptyList()
        }

        return AgentTypeRow(
            agentTypeId  = AgentTypeId(this[AgentTypeTable.agentTypeId]),
            name         = this[AgentTypeTable.name],
            model        = this[AgentTypeTable.model],
            systemPrompt = this[AgentTypeTable.systemPrompt],
            maxTokens    = this[AgentTypeTable.maxTokens],
            temperature  = this[AgentTypeTable.temperature],
            tools        = tools,
            dockerImage  = this[AgentTypeTable.dockerImage],
            spawnStrategy = this[AgentTypeTable.spawnStrategy],
            createdAt    = this[AgentTypeTable.createdAt],
            updatedAt    = this[AgentTypeTable.updatedAt],
        )
    }
}

/**
 * In-memory read model row for an AgentType.
 * Distinct from the domain [com.bigboote.domain.aggregates.AgentTypeState] —
 * this is a denormalised, query-optimised view populated from the Postgres read model.
 */
data class AgentTypeRow(
    val agentTypeId: AgentTypeId,
    val name: String,
    val model: String,
    val systemPrompt: String,
    val maxTokens: Int,
    val temperature: Double,
    val tools: List<String>,
    val dockerImage: String,
    val spawnStrategy: String,
    val createdAt: Instant,
    val updatedAt: Instant?,
)
