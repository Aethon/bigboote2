package com.bigboote.domain.aggregates

import com.bigboote.domain.events.ConversationEvent.*
import com.bigboote.domain.values.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock

class ConversationStateTest : StringSpec({

    val now = Clock.System.now()

    val createdEvent = ConversationCreated(
        convName = CollaboratorName.Channel("review"),
        members = listOf(
            CollaboratorName.Individual("lead-dev"),
            CollaboratorName.Individual("alice"),
        ),
        createdAt = now,
    )

    "apply ConversationCreated initializes state" {
        val state = ConversationState.EMPTY.apply(createdEvent)
        state.members.size shouldBe 2
    }

    "apply MemberAdded appends member" {
        val state = ConversationState.EMPTY
            .apply(createdEvent)
            .apply(MemberAdded(CollaboratorName.Individual("new-reviewer"), now))
        state.members.size shouldBe 3
        state.members shouldContain CollaboratorName.Individual("new-reviewer")
    }

    "apply MessagePosted does not change state" {
        val before = ConversationState.EMPTY.apply(createdEvent)
        val after = before.apply(
            MessagePosted(
                messageId = MessageId("msg:test1"),
                from = CollaboratorName.Individual("alice"),
                body = "Hello",
                postedAt = now,
            )
        )
        after shouldBe before
    }
})
