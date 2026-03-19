package com.bigboote.coordinator.aggregates.conversation

import com.bigboote.coordinator.aggregates.AggregateRepository
import com.bigboote.coordinator.api.error.DomainException
import com.bigboote.coordinator.api.error.ValidationException
import com.bigboote.domain.aggregates.ConversationState
import com.bigboote.domain.commands.ConversationCommand.*
import com.bigboote.domain.errors.DomainError
import com.bigboote.domain.events.ConversationEvent
import com.bigboote.domain.events.ConversationEvent.*
import com.bigboote.domain.values.ConvId
import com.bigboote.domain.values.EffortId
import com.bigboote.domain.values.StreamName
import com.bigboote.events.eventstore.ExpectedVersion
import kotlinx.datetime.Clock
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
    suspend fun handle(cmd: AddMember)
}

/**
 * Production implementation of [ConversationCommandHandler].
 *
 * Supported commands:
 * - [CreateChannel]: create a named channel conversation. Emits [ConversationCreated].
 * - [PostMessage]: post a message to a conversation. Emits [MessagePosted].
 *   For DM conversations that do not yet exist (state == EMPTY), auto-emits
 *   [ConversationCreated] first (on-demand DM creation) then [MessagePosted]
 *   in a single atomic append.
 * - [AddMember]: add a collaborator to an existing conversation. Emits [MemberAdded].
 *
 * See Architecture doc Section 6.2 and API Design doc Section 3.4.
 */
class ConversationCommandHandlerImpl(
    private val repo: AggregateRepository,
    private val clock: Clock,
) : ConversationCommandHandler {

    /**
     * Create a named channel conversation. The channel must not already exist on
     * this effort (NoStream guarantees idempotency on re-create attempts).
     */
    override suspend fun handle(cmd: CreateChannel) {
        val convId = cmd.convId
        if (convId !is ConvId.Channel) {
            throw ValidationException("CreateChannel requires a Channel ConvId, got: ${convId.value}")
        }

        val event = ConversationCreated(
            convName = cmd.convName,
            members = cmd.members,
            createdAt = clock.now(),
        )

        repo.append(
            StreamName.Conversation(cmd.effortId, convId),
            listOf(event),
            ExpectedVersion.NoStream,
        )

        logger.info("Channel created: {} in effort {}", convId.value, cmd.effortId)
    }

    /**
     * Post a message to a conversation.
     *
     * If [PostMessage.convId] is null, throws a [ValidationException].
     *
     * For DirectMessage conversations that have not yet been created (state == EMPTY),
     * this handler auto-creates the DM conversation on-demand before posting:
     *   - Determines the other party from the ConvId string.
     *   - Emits [ConversationCreated] and [MessagePosted] atomically.
     *
     * For Channel conversations in state EMPTY, throws [DomainError.ConversationNotFound]
     * (channels must be explicitly created via [CreateChannel]).
     */
    override suspend fun handle(cmd: PostMessage) {
        val rawConvId = cmd.convId
            ?: throw ValidationException("PostMessage.convId must not be null")

        val convId: ConvId = try {
            ConvId.parse(rawConvId)
        } catch (e: IllegalArgumentException) {
            throw ValidationException("Invalid convId: '$rawConvId': ${e.message}")
        }

        val loadedState = maybeLoadConversation(cmd.effortId, convId)

        if (loadedState == null) {
            // EMPTY sentinel — conversation does not exist yet
            when (convId) {
                is ConvId.DirectMessage -> {
                    // On-demand DM creation: emit ConversationCreated then MessagePosted atomically.
                    // Derive the canonical channel name as "DM: @party1 + @party2".
                    val dmName = com.bigboote.domain.values.CollaboratorName.Channel(
                        "dm:${convId.party1}+${convId.party2}"
                    )
                    val members = listOf(
                        com.bigboote.domain.values.CollaboratorName.from("@${convId.party1}"),
                        com.bigboote.domain.values.CollaboratorName.from("@${convId.party2}"),
                    )
                    val created = ConversationCreated(
                        convName = dmName,
                        members = members,
                        createdAt = clock.now(),
                    )
                    val posted = MessagePosted(
                        messageId = cmd.messageId,
                        from = cmd.from,
                        body = cmd.body,
                        postedAt = clock.now(),
                    )
                    repo.append(
                        StreamName.Conversation(cmd.effortId, convId),
                        listOf(created, posted),
                        ExpectedVersion.NoStream,
                    )
                    logger.info(
                        "DM conversation auto-created and first message posted: {} in effort {}",
                        convId.value, cmd.effortId
                    )
                }

                is ConvId.Channel -> {
                    throw DomainException(DomainError.ConversationNotFound(rawConvId))
                }
            }
        } else {
            // Conversation exists — just append the message.
            val event = MessagePosted(
                messageId = cmd.messageId,
                from = cmd.from,
                body = cmd.body,
                postedAt = clock.now(),
            )
            repo.append(
                StreamName.Conversation(cmd.effortId, convId),
                listOf(event),
                ExpectedVersion.Exact(loadedState.second),
            )
            logger.info(
                "Message posted to {} in effort {}: {}",
                convId.value, cmd.effortId, cmd.messageId
            )
        }
    }

    /**
     * Add a member to an existing conversation. Throws if the conversation does not exist.
     * Throws if the member is already in the conversation (idempotency check).
     */
    override suspend fun handle(cmd: AddMember) {
        val convId: ConvId = try {
            ConvId.parse(cmd.convId)
        } catch (e: IllegalArgumentException) {
            throw ValidationException("Invalid convId: '${cmd.convId}': ${e.message}")
        }

        val (state, version) = maybeLoadConversation(cmd.effortId, convId)
            ?: throw DomainException(DomainError.ConversationNotFound(cmd.convId))

        if (state.members.any { it.toString() == cmd.member.toString() }) {
            // Idempotent: member already present — no-op rather than error.
            logger.debug(
                "AddMember no-op: {} already in {} (effort {})",
                cmd.member, cmd.convId, cmd.effortId
            )
            return
        }

        val event = MemberAdded(
            member = cmd.member,
            addedAt = clock.now(),
        )

        repo.append(
            StreamName.Conversation(cmd.effortId, convId),
            listOf(event),
            ExpectedVersion.Exact(version),
        )

        logger.info(
            "Member {} added to {} in effort {}", cmd.member, cmd.convId, cmd.effortId
        )
    }

    // ---- private helpers ----

    private suspend fun maybeLoadConversation(
        effortId: EffortId,
        convId: ConvId,
    ) =
        repo.maybeLoad(
            ConversationEvent::class,
            StreamName.Conversation(effortId, convId),
            ConversationState::start,
            ConversationState::apply
        )
}
