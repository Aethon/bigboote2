package com.bigboote.domain.aggregates

import com.bigboote.domain.events.ConversationEvent
import com.bigboote.domain.events.ConversationEvent.*
import com.bigboote.domain.values.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock

class ConversationStateTest : StringSpec({

    val now = Clock.System.now()

    val streamName = StreamName.Conversation(EffortId("abc"), ConvId.Channel("def"))

    val createdEventEntry = EventLogEntryImpl(
        streamName = streamName,
        event = ConversationCreated(
            convName = CollaboratorName.Channel("review"),
            members = listOf(
                CollaboratorName.Individual("lead-dev"),
                CollaboratorName.Individual("alice"),
            ),
            createdAt = now,
        ),
        context = EventContext(0, 0)
    )

    val memberAddedEventEntry = EventLogEntryImpl(
        streamName = streamName,
        event = MemberAdded(CollaboratorName.Individual("new-reviewer"), now),
        context = EventContext(1, 14)
    )

    val messagePostedEventEntry = EventLogEntryImpl(
        streamName = streamName,
        event = MemberAdded(CollaboratorName.Individual("new-reviewer"), now),
        context = EventContext(1, 24)
    )

    "apply ConversationCreated initializes state" {
        val state = ConversationState.start(createdEventEntry)
        state.members.size shouldBe 2
    }

    "apply MemberAdded appends member" {
        val state = ConversationState.start(createdEventEntry)
            .apply(memberAddedEventEntry)
        state.members.size shouldBe 3
        state.members shouldContain CollaboratorName.Individual("new-reviewer")
    }

    "apply MessagePosted does not change state" {
        val before = ConversationState.start(createdEventEntry)
        val after = before.apply(messagePostedEventEntry)
        after shouldBe before
    }
})
