/*
 * Copyright 2024-2025 Kazimierz Pogoda / Xemantic
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xemantic.ai.anthropic.message

import com.xemantic.ai.anthropic.Response
import com.xemantic.ai.anthropic.cache.CacheControl
import com.xemantic.ai.anthropic.content.Content
import com.xemantic.ai.anthropic.content.Text
import com.xemantic.ai.anthropic.content.ToolUse
import com.xemantic.ai.anthropic.json.toPrettyJson
import com.xemantic.ai.anthropic.tool.Tool
import com.xemantic.ai.anthropic.tool.ToolChoice
import com.xemantic.ai.anthropic.usage.Usage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The roles that can be taken by entities in a conversation.
 */
enum class Role {

    @SerialName("user")
    USER,

    @SerialName("assistant")
    ASSISTANT

}

@Serializable
data class Metadata(
    @SerialName("user_id")
    val userId: String
)

@Serializable
data class MessageRequest(
    val model: String,
    val messages: List<Message>,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val metadata: Metadata?,
    @SerialName("stop_sequences")
    val stopSequences: List<String>?,
    val stream: Boolean?,
    val system: List<System>?,
    val temperature: Double?,
    @SerialName("tool_choice")
    val toolChoice: ToolChoice?,
    val tools: List<Tool>?,
    @SerialName("top_k")
    val topK: Int?,
    @SerialName("top_p")
    val topP: Double?
) {
    override fun toString(): String = toPrettyJson()

}

@Serializable
data class Message(
    val role: Role,
    val content: List<Content>
) {
    override fun toString(): String = toPrettyJson()

}

@Serializable
data class System(
    @SerialName("cache_control")
    val cacheControl: CacheControl? = null,
    val type: Type = Type.TEXT,
    val text: String? = null,
) {

    enum class Type {
        @SerialName("text")
        TEXT
    }

}

enum class StopReason {
    @SerialName("end_turn")
    END_TURN,

    @SerialName("max_tokens")
    MAX_TOKENS,

    @SerialName("stop_sequence")
    STOP_SEQUENCE,

    @SerialName("tool_use")
    TOOL_USE,

    @SerialName("pause_turn")
    PAUSE_TURN,

    @SerialName("refusal")
    REFUSAL
}

@Serializable
@SerialName("message")
data class MessageResponse(
    val model: String,
    val id: String,
    val role: Role,
    val content: List<Content>, // limited to Text and ToolUse
    @SerialName("stop_reason")
    val stopReason: StopReason?,
    @SerialName("stop_sequence")
    val stopSequence: String?,
    val usage: Usage
) : Response(type = "message") {

//    @Transient
//    internal lateinit var resolvedModel: AnthropicModel


    fun asContextMessage(): Message {
        var contextContext = content

        val index = content.indexOfLast { it is Text }
        if (index >= 0) {
            val text = content[index] as Text
            val trimmed = text.copy(text = text.text.trimEnd())
            val x = content.toMutableList()
            x[index] = trimmed
            contextContext = x.toList()
        }

        return Message(role = Role.ASSISTANT, content = contextContext)
    }

//    suspend fun useTools(
//        toolbox: Toolbox
//    ): Message {
//
//        check(stopReason == StopReason.TOOL_USE) {
//            "You can only use tools if the stopReason is TOOL_USE"
//        }
//
//        val results = content.filterIsInstance<ToolUse>().map { toolUse ->
//            toolbox.use(toolUse)
//        }
//
//        return Message {
//            content = results
//        }
//    }
//
//    val text: String?
//        get() = content.filterIsInstance<Text>().run {
//            if (isEmpty()) null
//            else joinToString(separator = "") { it.text }
//        }
//
//    val toolUse: ToolUse?
//        get() = toolUses.run {
//            if (isEmpty()) null else first()
//        }
//
//    inline fun <reified T> toolUseInput(
//        json: Json = anthropicJson
//    ) = toolUse!!.decodeInput<T>(json)
//
//    val toolUses: List<ToolUse> get() = content.filterIsInstance<ToolUse>()
//
//    val costWithUsage: CostWithUsage get() = CostWithUsage(
//        cost = resolvedModel.cost * usage,
//        usage = usage
//    )

    override fun toString() = toPrettyJson()

}

// TODO
//fun List<Message>.addCacheBreakpoint(
//    cacheControl: CacheControl? = null
//): List<Message> = mapLast { message ->
//    message.copy {
//        content = content.mapLast { contentElement ->
//            contentElement.alterCacheControl(
//                cacheControl ?: CacheControl.Ephemeral()
//            )
//        }
//    }
//}
