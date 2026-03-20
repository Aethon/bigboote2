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

package com.xemantic.ai.anthropic

import com.xemantic.ai.anthropic.error.ErrorResponse
import com.xemantic.ai.anthropic.json.anthropicJson
import com.xemantic.ai.anthropic.message.MessageRequest
import com.xemantic.ai.anthropic.message.MessageResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import org.aethon.anthropic.ClaudeApiCredentials

/**
 * The default Claude API base URL.
 */
const val DEFAULT_CLAUDE_API_BASE_URL: String = "https://api.anthropic.com/"

/**
 * The default version to be passed to the `anthropic-version` HTTP header of each API request.
 */
const val DEFAULT_ANTHROPIC_VERSION: String = "2023-06-01"

/**
 * The public constructor function which for the Anthropic API client.
 *
 * @param block the config block to set up the API access.
 */
fun Anthropic(
    credentials: ClaudeApiCredentials,
    block: ProductionClaudeApiClient.Config.() -> Unit = {}
): ProductionClaudeApiClient {
    val config = ProductionClaudeApiClient.Config().apply(block)
    return ProductionClaudeApiClient(
        credentials = credentials,
        anthropicVersion = config.anthropicVersion,
        anthropicBeta = if (config.anthropicBeta.isEmpty()) null else config.anthropicBeta.joinToString(","),
        apiBase = config.apiBase,
        directBrowserAccess = config.directBrowserAccess,
        logLevel = if (config.logHttp) LogLevel.ALL else LogLevel.NONE
    )
}

class ProductionClaudeApiClient(
    private val credentials: ClaudeApiCredentials,
    val anthropicVersion: String,
    val anthropicBeta: String?,
    val apiBase: String,
    val directBrowserAccess: Boolean,
    val logLevel: LogLevel
) : ClaudeApiClient {

    class Config {
        var anthropicVersion: String = DEFAULT_ANTHROPIC_VERSION
        var anthropicBeta: List<String> = emptyList()
        var apiBase: String = DEFAULT_CLAUDE_API_BASE_URL

        var directBrowserAccess: Boolean = false
        var logHttp: Boolean = false

        operator fun Beta.unaryPlus() {
            anthropicBeta += this.id
        }

    }

    enum class Beta(val id: String) {
        OUTPUT_128K_2025_02_19("output-128k-2025-02-19"),
        COMPUTER_USE_2025_01_24("computer-use-2025-01-24"),
        COMPUTER_USE_2024_10_22("computer-use-2024-10-22"),
        WEB_SEARCH_2025_03_05("web-search-2025-03-05"),
        WEB_FETCH_2025_09_10("web-fetch-2025-09-10")
    }

    private val client = HttpClient(CIO) {

        val retriableResponses = setOf(
            HttpStatusCode.RequestTimeout,
            HttpStatusCode.Conflict,
            HttpStatusCode.TooManyRequests,
            HttpStatusCode.InternalServerError
        )

        // declaration order matters :(
        install(SSE)

        install(ContentNegotiation) {
            json(anthropicJson)
        }

        if (logLevel != LogLevel.NONE) {
            install(Logging) {
                level = logLevel
            }
        }

        install(HttpRequestRetry) {
            exponentialDelay()
            maxRetries = 5
            retryIf { _, response ->
                response.status in retriableResponses || response.status.value >= 500
            }
        }

        defaultRequest {
            url(apiBase)

            credentials.applyToRequest { header, value -> header(header, value) }

            header("anthropic-version", anthropicVersion)
            if (anthropicBeta != null) {
                header("anthropic-beta", anthropicBeta)
            }
            if (directBrowserAccess) {
                header("anthropic-dangerous-direct-browser-access", true)
            }
        }
    }

    override suspend fun sendMessage(request: MessageRequest): ClaudeApiClient.MessageResult {
        val apiResponse = client.post("/v1/messages") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return when (val response = apiResponse.body<Response>()) {
            is MessageResponse -> ClaudeApiClient.MessageResult.Message(response)
            is ErrorResponse -> ClaudeApiClient.MessageResult.Error(apiResponse.status, response)
            else -> throw RuntimeException(
                "Unsupported response: $response"
            ) // should never happen
        }
    }
}

interface ClaudeApiClient {
    suspend fun sendMessage(request: MessageRequest): MessageResult

    sealed class MessageResult {
        data class Message(val message: MessageResponse) : MessageResult()
        data class Error(val httpStatus: HttpStatusCode, val error: ErrorResponse) : MessageResult()
    }
}