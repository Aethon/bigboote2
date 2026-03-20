package com.bigboote.domain.aggregates

import com.bigboote.domain.events.GroupChannelEvent.*
import com.bigboote.domain.events.EventLogEntry
import com.bigboote.domain.events.GroupChannelEvent
import com.bigboote.domain.events.NoContextStreamState
import com.bigboote.domain.events.StreamStateStarter
import com.bigboote.domain.values.*

data class GroupChannelState(
    val members: List<CollaboratorName.Individual>
) : NoContextStreamState<GroupChannelEvent, GroupChannelState>() {
    override fun apply(event: GroupChannelEvent): GroupChannelState {
        return when (event) {
            is ChannelCreated -> throw IllegalStateException("Channel already created")
            is MembersAdded -> copy(members = members + event.members)
            is MessagePosted -> this
        }
    }

    companion object: StreamStateStarter<GroupChannelEvent, GroupChannelState> {
        override fun start(entry: EventLogEntry<GroupChannelEvent>): GroupChannelState {
            val event = entry.event as? ChannelCreated
                ?: throw IllegalArgumentException("Must start with ChannelCreated event")
            return GroupChannelState(
                members = event.members
            )
        }
    }
}
