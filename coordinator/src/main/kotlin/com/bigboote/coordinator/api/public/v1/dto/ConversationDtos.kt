package com.bigboote.coordinator.api.public.v1.dto

import kotlinx.serialization.Serializable

// ---- request bodies ----

/**
 * Request body for POST /api/v1/efforts/{effortId}/conversations/create-channel.
 *
 * [name] is the bare channel name without prefix — e.g. "review" (not "#review").
 * The handler will prepend "#" and convert to ConvId.Channel.
 *
 * [members] is a list of collaborator name strings including their prefix,
 * e.g. ["@alice", "@lead-dev"]. At least one member is required.
 */
@Serializable
data class CreateChannelRequest(
    val name: String,
    val members: List<String>,
)

/**
 * Request body for POST /api/v1/efforts/{effortId}/conversations/{convId}/members.
 *
 * [member] is a prefixed collaborator name, e.g. "@alice".
 */
@Serializable
data class AddMemberRequest(
    val member: String,
)

// ---- response bodies ----

/** Response for POST /api/v1/efforts/{effortId}/conversations/create-channel (201 Created). */
@Serializable
data class CreateChannelResponse(
    val convId: String,
    val convName: String,
    val members: List<String>,
    val createdAt: String,
)

/** Response for POST /api/v1/efforts/{effortId}/conversations/{convId}/members (200 OK). */
@Serializable
data class AddMemberResponse(
    val convId: String,
    val member: String,
)

/** Summary view of a Conversation for list responses. */
@Serializable
data class ConversationSummaryResponse(
    val convId: String,
    val convName: String,
    val members: List<String>,
    val createdAt: String,
)

/** Response for GET /api/v1/efforts/{effortId}/conversations (200 OK). */
@Serializable
data class ConversationListResponse(
    val conversations: List<ConversationSummaryResponse>,
)

/** Single message view for message list responses. */
@Serializable
data class MessageResponse(
    val messageId: String,
    val from: String,
    val body: String,
    val postedAt: String,
)

/** Response for GET /api/v1/efforts/{effortId}/conversations/{convId}/messages (200 OK). */
@Serializable
data class MessageListResponse(
    val convId: String,
    val messages: List<MessageResponse>,
    val from: Int,
    val limit: Int,
)
