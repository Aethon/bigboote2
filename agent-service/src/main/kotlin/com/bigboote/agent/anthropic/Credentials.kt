package org.aethon.anthropic

import kotlinx.io.files.Path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json.Default.decodeFromString
import java.io.File

interface ClaudeApiCredentials {
    fun applyToRequest(setHeader: (String, Any?) -> Unit)
}

class FileCredentials(val path: Path) : ClaudeApiCredentials {

    override fun applyToRequest(setHeader: (String, Any?) -> Unit) {

        val credentials = decodeFromString<ApiKeyCredentials>(File(path.toString()).readText())

        setHeader("x-api-key", credentials.apiKey)
    }
}

@Serializable
data class ApiKeyCredentials(
    @SerialName("api_key")
    val apiKey: String
) {
}