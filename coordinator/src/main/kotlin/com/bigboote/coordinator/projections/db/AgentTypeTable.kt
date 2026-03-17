package com.bigboote.coordinator.projections.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed table definition for the AgentType read model.
 *
 * Populated by [com.bigboote.coordinator.projections.AgentTypeSummaryProjection]
 * from AgentTypeEvent catch-up subscriptions.
 *
 * All fields except [updatedAt] are non-nullable because every AgentType row
 * originates from an AgentTypeCreated event that supplies all required fields.
 *
 * See Architecture doc Section 8.2.
 */
object AgentTypeTable : Table("agent_types") {
    val agentTypeId  = varchar("agent_type_id", 128)
    val name         = varchar("name", 128)
    val model        = varchar("model", 128)
    val systemPrompt = text("system_prompt")
    val maxTokens    = integer("max_tokens")
    val temperature  = double("temperature")
    val tools        = text("tools").default("[]")    // JSON array of tool-name strings
    val dockerImage  = varchar("docker_image", 256)
    val spawnStrategy = varchar("spawn_strategy", 32)
    val createdAt    = timestamp("created_at")
    val updatedAt    = timestamp("updated_at").nullable()

    override val primaryKey = PrimaryKey(agentTypeId)
}
