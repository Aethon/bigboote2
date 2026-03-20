package com.bigboote.coordinator.aggregates.conversation

import com.bigboote.coordinator.aggregates.AggregateRepository
import com.bigboote.coordinator.api.error.DomainException
import com.bigboote.coordinator.api.error.ValidationException
import com.bigboote.domain.aggregates.GroupChannelState
import com.bigboote.domain.commands.ConversationCommand.*
import com.bigboote.domain.errors.DomainError
import com.bigboote.domain.events.DirectMessageEvent
import com.bigboote.domain.events.EventContext
import com.bigboote.domain.events.GroupChannelEvent
import com.bigboote.domain.values.MessageId
import com.bigboote.domain.values.StreamName
import com.bigboote.events.eventstore.ExpectedVersion
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(
    "com.bigboote.coordinator.aggregates.conversation.ConversationCommandHandlerImpl"
)

/**
 * Handles all Conversation aggregate commands.
 *
 * Extracted so that tests can mock it without requiring the `open` modifier.
 * The sole production implementation is [ConversationCommandHandlerImpl].
 *
 * See Architecture doc Section 6.2 and API Design doc Section 3.4.
 */
interface ConversationCommandHandler {
    suspend fun handle(cmd: CreateChannel)
    suspend fun handle(cmd: PostMessage)
    suspend fun handle(cmd: AddMembers)
    suspend fun handle(cmd: PostDirectMessage)
}

/**
 * Production implementation of [ConversationCommandHandler].
 *
 * Supported commands:
 * - [CreateChannel]: create a named group channel. Emits [GroupChannelEvent.ChannelCreated].
 * - [PostMessage]: post a message to a group channel. Emits [GroupChannelEvent.ChannelMessagePosted].
 *   The recipient set (`to`) is derived from the channel's current membership minus the sender.
 * - [AddMembers]: add collaborators to an existing channel. Emits [GroupChannelEvent.MembersAdded].
 * - [PostDirectMessage]: post a direct message to an individual. Emits [DirectMessageEvent.DirectMessagePosted].
 *   DM streams are created on-demand (no explicit channel creation required).
 *
 * See Architecture doc Section 6.2 and API Design doc Section 3.4.
 */
class ConversationCommandHandlerImpl(
    private val repo: AggregateRepository,
) : ConversationCommandHandler {

    /**
     * Create a named group channel. The channel must not already exist on
     * this effort (NoStream guarantees idempotency on re-create attempts).
     */
    override suspend fun handle(cmd: CreateChannel) {
        val event = GroupChannelEvent.ChannelCreated(
            members = cmd.members.filterIsInstance<com.bigboote.domain.values.CollaboratorName.Individual>(),
        )

        repo.append(
            StreamName.GroupChannel(cmd.effortId, cmd.channelName),
            listOf(event),
            ExpectedVersion.NoStream,
        )

        logger.info("Channel created: {} in effort {}", cmd.channelName.simple, cmd.effortId)
    }

    /**
     * Post a message to a group channel.
     *
     * Loads the channel state to determine the full recipient list.
     * Throws [DomainError.ConversationNotFound] if the channel does not exist.
     */
    override suspend fun handle(cmd: PostMessage) {
        val (state, version) = loadChannel(cmd.effortId, cmd.channelName)
            ?: throw DomainException(DomainError.ConversationNotFound(cmd.channelName.simple))

        val to = state.members.filter { it != cmd.from }.toSet()

        val event = GroupChannelEvent.ChannelMessagePosted(
            messageId = MessageId.generate(),
            from = cmd.from,
            to = to,
            body = cmd.body,
        )

        repo.append(
            StreamName.GroupChannel(cmd.effortId, cmd.channelName),
            listOf(event),
            ExpectedVersion.Exact(version),
        )

        logger.info(
            "Message posted to #{} in effort {} by {}",
            cmd.channelName.simple, cmd.effortId, cmd.from.simple
        )
    }

    /**
     * Add members to an existing group channel.
     *
     * Throws [DomainError.ConversationNotFound] if the channel does not exist.
     * Members already present are silently filtered out (idempotent).
     */
    override suspend fun handle(cmd: AddMembers) {
        val (state, version) = loadChannel(cmd.effortId, cmd.channelName)
            ?: throw DomainException(DomainError.ConversationNotFound(cmd.channelName.simple))

        val newMembers = cmd.members.filter { m -> state.members.none { it == m } }.toSet()
        if (newMembers.isEmpty()) {
            logger.debug(
                "AddMembers no-op: all members already in #{} (effort {})",
                cmd.channelName.simple, cmd.effortId
            )
            return
        }

        val event = GroupChannelEvent.MembersAdded(members = newMembers)

        repo.append(
            StreamName.GroupChannel(cmd.effortId, cmd.channelName),
            listOf(event),
            ExpectedVersion.Exact(version),
        )

        logger.info(
            "Members {} added to #{} in effort {}",
            newMembers.map { it.simple }, cmd.channelName.simple, cmd.effortId
        )
    }

    /**
     * Post a direct message to an individual collaborator.
     *
     * DM streams are created on-demand — no explicit setup is required before posting.
     * Uses [ExpectedVersion.Any] to allow the first message to create the stream.
     */
    override suspend fun handle(cmd: PostDirectMessage) {
        val event = DirectMessageEvent.DirectMessagePosted(
            messageId = MessageId.generate(),
            from = cmd.from,
            body = cmd.body,
        )

        repo.append(
            StreamName.DirectMessage(cmd.effortId, cmd.toName),
            listOf(event),
            ExpectedVersion.Any,
        )

        logger.info(
            "Direct message sent from @{} to @{} in effort {}",
            cmd.from.simple, cmd.toName.simple, cmd.effortId
        )
    }

    // ---- private helpers ----

    private suspend fun loadChannel(
        effortId: com.bigboote.domain.values.EffortId,
        channelName: com.bigboote.domain.values.CollaboratorName.Channel,
    ) = repo.maybeLoad(
        GroupChannelEvent::class,
        StreamName.GroupChannel(effortId, channelName),
        GroupChannelState::start,
        { state, entry -> state.apply(entry) },
    )
}
