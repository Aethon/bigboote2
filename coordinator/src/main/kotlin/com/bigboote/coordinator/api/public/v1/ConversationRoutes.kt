package com.bigboote.coordinator.api.public.v1

import com.bigboote.coordinator.aggregates.conversation.ConversationCommandHandler
import com.bigboote.coordinator.api.error.DomainException
import com.bigboote.coordinator.api.error.ValidationException
import com.bigboote.coordinator.api.public.v1.dto.*
import com.bigboote.coordinator.projections.ConversationProjection
import com.bigboote.coordinator.projections.repositories.ConversationReadRepository
import com.bigboote.coordinator.projections.repositories.ConversationRow
import com.bigboote.coordinator.projections.repositories.MessageRow
import com.bigboote.domain.commands.ConversationCommand.*
import com.bigboote.domain.errors.DomainError
import com.bigboote.domain.values.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.bigboote.coordinator.api.public.v1.ConversationRoutes")

/**
 * Conversation API endpoints mounted under /api/v1/efforts/{effortId}/conversations.
 *
 * POST /api/v1/efforts/{effortId}/conversations/create-channel
 *      → CreateChannel command; returns 201 with [CreateChannelResponse].
 *
 * GET  /api/v1/efforts/{effortId}/conversations
 *      → list all conversations for the effort; returns 200 with [ConversationListResponse].
 *
 * GET  /api/v1/efforts/{effortId}/conversations/{convId}/messages?from=0&limit=50
 *      → paginated message list for a conversation; returns 200 with [MessageListResponse].
 *
 * POST /api/v1/efforts/{effortId}/conversations/{convId}/members
 *      → AddMember command; returns 200 with [AddMemberResponse].
 *
 * Note: "PostMessage" is NOT exposed via REST in Phase 11. It is used internally
 * by the Agent Gateway (Phase 12) and WebSocket (Phase 13) layers.
 *
 * See API Design doc Section 3.4.
 */
fun Route.conversationRoutes() {
    val commandHandler by inject<ConversationCommandHandler>()
    val projection     by inject<ConversationProjection>()
    val readRepo       by inject<ConversationReadRepository>()

    route("/efforts/{effortId}/conversations") {

        // ------------------------------------------------------------------
        // POST /api/v1/efforts/{effortId}/conversations/create-channel
        // ------------------------------------------------------------------
        post("/create-channel") {
            val effortId = parseEffortIdParam(call)
            val req = call.receive<CreateChannelRequest>()
            validateCreateChannelRequest(req)

            val channelName = req.name.trim()
            val convId = ConvId.channel(channelName)

            val members: List<CollaboratorName> = req.members.map { nameStr ->
                try {
                    CollaboratorName.from(nameStr)
                } catch (e: IllegalArgumentException) {
                    throw ValidationException("Invalid member name '$nameStr': ${e.message}")
                }
            }

            // Use the channel name as the convName (prefixed "#channel")
            val convName = CollaboratorName.Channel(channelName)

            val cmd = CreateChannel(
                convId   = convId,
                effortId = effortId,
                convName = convName,
                members  = members,
            )

            commandHandler.handle(cmd)

            // Begin tracking the new conversation stream for read-your-writes consistency.
            projection.trackConversation(effortId, convId)

            logger.info("Channel created via API: {} in effort {}", convId.value, effortId)

            call.respond(
                HttpStatusCode.Created,
                CreateChannelResponse(
                    convId    = convId.value,
                    convName  = convName.toString(),
                    members   = req.members,
                    createdAt = Clock.System.now().toString(),
                )
            )
        }

        // ------------------------------------------------------------------
        // GET /api/v1/efforts/{effortId}/conversations
        // ------------------------------------------------------------------
        get {
            val effortId = parseEffortIdParam(call)
            val conversations = readRepo.list(effortId)
            call.respond(
                HttpStatusCode.OK,
                ConversationListResponse(
                    conversations = conversations.map { it.toSummaryResponse() }
                )
            )
        }

        // ------------------------------------------------------------------
        // GET /api/v1/efforts/{effortId}/conversations/{convId}/messages
        // ------------------------------------------------------------------
        get("/{convId}/messages") {
            val effortId = parseEffortIdParam(call)
            val convIdStr = parseConvIdParam(call)
            val from  = call.request.queryParameters["from"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50

            // Verify the conversation exists before returning messages
            readRepo.get(effortId, convIdStr)
                ?: throw DomainException(DomainError.ConversationNotFound(convIdStr))

            val messages = readRepo.getMessages(effortId, convIdStr, from, limit)

            call.respond(
                HttpStatusCode.OK,
                MessageListResponse(
                    convId   = convIdStr,
                    messages = messages.map { it.toMessageResponse() },
                    from     = from,
                    limit    = limit,
                )
            )
        }

        // ------------------------------------------------------------------
        // POST /api/v1/efforts/{effortId}/conversations/{convId}/members
        // ------------------------------------------------------------------
        post("/{convId}/members") {
            val effortId = parseEffortIdParam(call)
            val convIdStr = parseConvIdParam(call)
            val req = call.receive<AddMemberRequest>()

            if (req.member.isBlank()) throw ValidationException("'member' must not be blank")

            val member: CollaboratorName = try {
                CollaboratorName.from(req.member)
            } catch (e: IllegalArgumentException) {
                throw ValidationException("Invalid member name '${req.member}': ${e.message}")
            }

            val cmd = AddMember(
                convId   = convIdStr,
                effortId = effortId,
                member   = member,
            )

            commandHandler.handle(cmd)

            logger.info("Member {} added to {} in effort {} via API", req.member, convIdStr, effortId)

            call.respond(
                HttpStatusCode.OK,
                AddMemberResponse(convId = convIdStr, member = req.member)
            )
        }
    }
}

// ------------------------------------------------------------------ private helpers

private fun parseEffortIdParam(call: ApplicationCall): EffortId {
    val raw = call.parameters["effortId"]
    if (raw.isNullOrBlank()) throw ValidationException("Missing effortId path parameter")
    return try {
        EffortId(raw)
    } catch (e: IllegalArgumentException) {
        throw ValidationException("Invalid effortId: '$raw'")
    }
}

private fun parseConvIdParam(call: ApplicationCall): String {
    val raw = call.parameters["convId"]
    if (raw.isNullOrBlank()) throw ValidationException("Missing convId path parameter")
    // Validate it parses as a valid ConvId
    try {
        ConvId.parse(raw)
    } catch (e: IllegalArgumentException) {
        throw ValidationException("Invalid convId: '$raw': ${e.message}")
    }
    return raw
}

private fun validateCreateChannelRequest(req: CreateChannelRequest) {
    if (req.name.isBlank()) throw ValidationException("'name' must not be blank")
    if (req.members.isEmpty()) throw ValidationException("'members' must not be empty")
    req.members.forEach { name ->
        if (name.isBlank()) throw ValidationException("member name must not be blank")
    }
}

private fun ConversationRow.toSummaryResponse(): ConversationSummaryResponse =
    ConversationSummaryResponse(
        convId    = convId,
        convName  = convName,
        members   = members,
        createdAt = createdAt.toString(),
    )

private fun MessageRow.toMessageResponse(): MessageResponse =
    MessageResponse(
        messageId = messageId,
        from      = fromName,
        body      = body,
        postedAt  = postedAt.toString(),
    )
