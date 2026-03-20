package org.aethon.agentrunner

import com.xemantic.ai.anthropic.message.Metadata
import com.xemantic.ai.anthropic.message.System
import com.xemantic.ai.anthropic.tool.Tool
import com.xemantic.ai.anthropic.tool.ToolChoice
import kotlinx.serialization.SerialName

class AgentLoopConfig(
    val model: String,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val metadata: com.xemantic.ai.anthropic.message.Metadata? = null,
    val system: List<com.xemantic.ai.anthropic.message.System>? = null,
    val temperature: Double = 0.0,
    @SerialName("tool_choice")
    val toolChoice: com.xemantic.ai.anthropic.tool.ToolChoice? = null,
    val tools: List<com.xemantic.ai.anthropic.tool.Tool>? = null,
    @SerialName("top_k")
    val topK: Int? = null,
    @SerialName("top_p")
    val topP: Double? = null
)
