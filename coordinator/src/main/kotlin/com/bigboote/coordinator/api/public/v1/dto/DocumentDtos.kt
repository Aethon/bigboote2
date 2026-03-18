package com.bigboote.coordinator.api.public.v1.dto

import kotlinx.serialization.Serializable

// ---- request bodies ----

/**
 * Request body for POST /api/v1/efforts/{effortId}/documents/create.
 *
 * [name]     — filename, e.g. "oauth2-design.md"
 * [mimeType] — MIME type, e.g. "text/markdown" or "text/plain"
 * [content]  — full UTF-8 document body
 */
@Serializable
data class CreateDocumentRequest(
    val name: String,
    val mimeType: String,
    val content: String,
)

/**
 * Request body for POST /api/v1/efforts/{effortId}/documents/{docId}/update.
 *
 * [content] — the new full UTF-8 document body (replaces existing content)
 */
@Serializable
data class UpdateDocumentRequest(
    val content: String,
)

// ---- response bodies ----

/**
 * Response for POST /api/v1/efforts/{effortId}/documents/create (201 Created).
 *
 * Returns the generated [docId], [name], deterministic [s3Key], and audit fields.
 */
@Serializable
data class CreateDocumentResponse(
    val docId:     String,
    val name:      String,
    val s3Key:     String,
    val createdAt: String,
    val createdBy: String,
)

/**
 * Summary view of a Document returned in list responses.
 * Content is intentionally omitted to keep list responses lean.
 */
@Serializable
data class DocumentSummaryResponse(
    val docId:     String,
    val name:      String,
    val mimeType:  String,
    val s3Key:     String,
    val createdAt: String,
    val createdBy: String,
)

/**
 * Detail view of a Document including its content body.
 * Returned by GET /api/v1/efforts/{effortId}/documents/{docId}.
 */
@Serializable
data class DocumentDetailResponse(
    val docId:     String,
    val effortId:  String,
    val name:      String,
    val mimeType:  String,
    val s3Key:     String,
    val content:   String,
    val createdAt: String,
    val createdBy: String,
)

/** Response for GET /api/v1/efforts/{effortId}/documents (200 OK). */
@Serializable
data class DocumentListResponse(
    val documents: List<DocumentSummaryResponse>,
)

/** Response for POST /api/v1/efforts/{effortId}/documents/{docId}/delete (200 OK). */
@Serializable
data class DeleteDocumentResponse(
    val docId:   String,
    val deleted: Boolean = true,
)
