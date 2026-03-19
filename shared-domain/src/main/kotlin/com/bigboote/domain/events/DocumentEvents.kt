package com.bigboote.domain.events

import com.bigboote.domain.values.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Events for the Document stream: `/effort:{id}/docs`
 *
 * [EffortId] is inherent to [StreamName.Docs] and is no longer duplicated in event
 * payloads. Retrieve it via [StreamName.Docs.effortId] from
 * [com.bigboote.events.eventstore.EventEnvelope.streamName].
 *
 * See Architecture doc Change Document v1.0 Section 5.6.
 */
@Serializable
sealed interface DocumentEvent {

    @Serializable
    @SerialName("DocumentCreated")
    data class DocumentCreated(
        val documentId: DocumentId,
        val name: String,
        val mimeType: String,
        val s3Key: String,
        val createdBy: CollaboratorName,
        val createdAt: Instant,
    ) : DocumentEvent

    @Serializable
    @SerialName("DocumentUpdated")
    data class DocumentUpdated(
        val documentId: DocumentId,
        val s3Key: String,
        val updatedBy: CollaboratorName,
        val updatedAt: Instant,
    ) : DocumentEvent

    @Serializable
    @SerialName("DocumentDeleted")
    data class DocumentDeleted(
        val documentId: DocumentId,
        val deletedBy: CollaboratorName,
        val deletedAt: Instant,
    ) : DocumentEvent
}

/**
 * Safely cast an untyped [StreamName] to [StreamName.Docs].
 * Use this in [EventStore.subscribeToAll] handlers after a type-check on `envelope.data`.
 */
fun StreamName<*>.asDocsStream(): StreamName.Docs =
    this as? StreamName.Docs
        ?: error("Expected StreamName.Docs but got ${this::class.simpleName} for path '$path'")
