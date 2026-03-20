package com.bigboote.domain.aggregates

import com.bigboote.domain.events.EventContext
import com.bigboote.domain.events.EventLogEntry
import com.bigboote.domain.events.GroupChannelEvent.*
import com.bigboote.domain.values.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class GroupChannelStateTest : StringSpec({

    val streamName = StreamName.GroupChannel(EffortId("effort:abc"), CollaboratorName.Channel("def"))

    val createdEventEntry = EventLogEntry(
        streamName = streamName,
        event = ChannelCreated(
            members = listOf(
                CollaboratorName.Individual("lead-dev"),
                CollaboratorName.Individual("alice"),
            )
        ),
        context = EventContext(0, 0)
    )

    val memberAddedEventEntry = EventLogEntry(
        streamName = streamName,
        event = MembersAdded(setOf(CollaboratorName.Individual("new-reviewer"))),
        context = EventContext(1, 14)
    )

    val messagePostedEventEntry = EventLogEntry(
        streamName = streamName,
        event = ChannelMessagePosted(
            messageId = MessageId("msg:123"),
            from = CollaboratorName.Individual("alice"),
            to = setOf(CollaboratorName.Individual("lead-dev")),
            body = "Hello, world!"
        ),
        context = EventContext(1, 24)
    )

    "apply ConversationCreated initializes state" {
        val state = GroupChannelState.start(createdEventEntry)
        state.members.size shouldBe 2
    }

    "apply MemberAdded appends member" {
        val state = GroupChannelState.start(createdEventEntry)
            .apply(memberAddedEventEntry)
        state.members.size shouldBe 3
        state.members shouldContain CollaboratorName.Individual("new-reviewer")
    }

    "apply MessagePosted does not change state" {
        val before = GroupChannelState.start(createdEventEntry)
        val after = before.apply(messagePostedEventEntry)
        after shouldBe before
    }
})
