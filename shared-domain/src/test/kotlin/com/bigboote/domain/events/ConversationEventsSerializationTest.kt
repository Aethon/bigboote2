package com.bigboote.domain.events

import com.bigboote.domain.events.ConversationEvent.*
import com.bigboote.domain.values.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class ConversationEventsSerializationTest : StringSpec({

    val json = Json { encodeDefaults = true }
    val now = Clock.System.now()

    "ConversationCreated channel round-trip" {
        val event: ConversationEvent = ConversationCreated(
            convName = CollaboratorName.Channel("review"),
            members = listOf(
                CollaboratorName.Individual("lead-dev"),
                CollaboratorName.Individual("reviewer"),
                CollaboratorName.Individual("alice"),
            ),
            createdAt = now,
        )
        val encoded = json.encodeToString<ConversationEvent>(event)
        val decoded = json.decodeFromString<ConversationEvent>(encoded)
        decoded shouldBe event
    }

    "ConversationCreated dm round-trip" {
        val event: ConversationEvent = ConversationCreated(
            convName = CollaboratorName.Individual("alice"),
            members = listOf(
                CollaboratorName.Individual("alice"),
                CollaboratorName.Individual("lead-dev"),
            ),
            createdAt = now,
        )
        val encoded = json.encodeToString<ConversationEvent>(event)
        val decoded = json.decodeFromString<ConversationEvent>(encoded)
        decoded shouldBe event
    }

    "MemberAdded round-trip" {
        val event: ConversationEvent = MemberAdded(
            member = CollaboratorName.Individual("new-reviewer"),
            addedAt = now,
        )
        val encoded = json.encodeToString<ConversationEvent>(event)
        val decoded = json.decodeFromString<ConversationEvent>(encoded)
        decoded shouldBe event
    }

    "MessagePosted round-trip" {
        val event: ConversationEvent = MessagePosted(
            messageId = MessageId("msg:test1"),
            from = CollaboratorName.Individual("alice"),
            body = "One comment on the token expiry logic.",
            postedAt = now,
        )
        val encoded = json.encodeToString<ConversationEvent>(event)
        val decoded = json.decodeFromString<ConversationEvent>(encoded)
        decoded shouldBe event
    }

    "MessagePosted from system round-trip" {
        val event: ConversationEvent = MessagePosted(
            messageId = MessageId("msg:sys1"),
            from = CollaboratorName.Individual("system"),
            body = "Effort has been paused by the Coordinator.",
            postedAt = now,
        )
        val encoded = json.encodeToString<ConversationEvent>(event)
        val decoded = json.decodeFromString<ConversationEvent>(encoded)
        decoded shouldBe event
    }
})
