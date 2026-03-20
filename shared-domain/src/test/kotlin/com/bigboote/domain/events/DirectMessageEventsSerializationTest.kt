package com.bigboote.domain.events

import com.bigboote.domain.events.DirectMessageEvent.*
import com.bigboote.domain.values.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class DirectMessageEventsSerializationTest : StringSpec({

    val json = Json { encodeDefaults = true }

    "MessagePosted round-trip" {
        val event: DirectMessageEvent = DirectMessagePosted(
            messageId = MessageId("msg:test1"),
            from = CollaboratorName.Individual("alice"),
            body = "One comment on the token expiry logic."
        )
        val encoded = json.encodeToString<DirectMessageEvent>(event)
        val decoded = json.decodeFromString<DirectMessageEvent>(encoded)
        decoded shouldBe event
    }

    "MessagePosted from system round-trip" {
        val event: DirectMessageEvent = DirectMessagePosted(
            messageId = MessageId("msg:sys1"),
            from = CollaboratorName.Individual("system"),
            body = "Effort has been paused by the Coordinator."
        )
        val encoded = json.encodeToString<DirectMessageEvent>(event)
        val decoded = json.decodeFromString<DirectMessageEvent>(encoded)
        decoded shouldBe event
    }
})
