package com.bigboote.domain.commands

import com.bigboote.domain.values.AgentTypeId

sealed interface AgentTypeCommand {

    data class CreateAgentType(
        val agentTypeId: AgentTypeId,
        val name: String,
        val model: String,
        val systemPrompt: String,
        val maxTokens: Int,
        val temperature: Double? = null,
        val tools: List<String>? = null,
        val dockerImage: String,
        val spawnStrategy: String,
    ) : AgentTypeCommand

    data class UpdateAgentType(
        val agentTypeId: AgentTypeId,
        val name: String? = null,
        val model: String? = null,
        val systemPrompt: String? = null,
        val maxTokens: Int? = null,
        val temperature: Double? = null,
        val tools: List<String>? = null,
        val dockerImage: String? = null,
        val spawnStrategy: String? = null,
    ) : AgentTypeCommand
}
