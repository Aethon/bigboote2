package com.bigboote.domain.events

import com.bigboote.domain.events.GroupChannelEvent.*
import com.bigboote.domain.values.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class GroupChannelEventsSerializationTest : StringSpec({

    val json = Json { encodeDefaults = true }

    "ConversationCreated channel round-trip" {
        val event: GroupChannelEvent = ChannelCreated(
            members = listOf(
                CollaboratorName.Individual("lead-dev"),
                CollaboratorName.Individual("reviewer"),
                CollaboratorName.Individual("alice"),
            )
        )
        val encoded = json.encodeToString<GroupChannelEvent>(event)
        val decoded = json.decodeFromString<GroupChannelEvent>(encoded)
        decoded shouldBe event
    }

    "ConversationCreated dm round-trip" {
        val event: GroupChannelEvent = ChannelCreated(
            members = listOf(
                CollaboratorName.Individual("alice"),
                CollaboratorName.Individual("lead-dev"),
            )
        )
        val encoded = json.encodeToString<GroupChannelEvent>(event)
        val decoded = json.decodeFromString<GroupChannelEvent>(encoded)
        decoded shouldBe event
    }

    "MemberAdded round-trip" {
        val event: GroupChannelEvent = MembersAdded(
            members = setOf(CollaboratorName.Individual("new-reviewer"))
        )
        val encoded = json.encodeToString<GroupChannelEvent>(event)
        val decoded = json.decodeFromString<GroupChannelEvent>(encoded)
        decoded shouldBe event
    }

    "MessagePosted round-trip" {
        val event: GroupChannelEvent = MessagePosted(
            messageId = MessageId("msg:test1"),
            from = CollaboratorName.Individual("alice"),
            to = setOf(CollaboratorName.Individual("lead-dev")),
            body = "One comment on the token expiry logic."
        )
        val encoded = json.encodeToString<GroupChannelEvent>(event)
        val decoded = json.decodeFromString<GroupChannelEvent>(encoded)
        decoded shouldBe event
    }

    "MessagePosted from system round-trip" {
        val event: GroupChannelEvent = MessagePosted(
            messageId = MessageId("msg:sys1"),
            from = CollaboratorName.Individual("system"),
            to = setOf(CollaboratorName.Individual("lead-dev")),
            body = "Effort has been paused by the Coordinator."
        )
        val encoded = json.encodeToString<GroupChannelEvent>(event)
        val decoded = json.decodeFromString<GroupChannelEvent>(encoded)
        decoded shouldBe event
    }
})
