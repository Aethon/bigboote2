package com.bigboote.domain.events

import com.bigboote.domain.events.DocumentEvent.*
import com.bigboote.domain.values.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class DocumentEventsSerializationTest : StringSpec({

    val json = Json { encodeDefaults = true }
    val now = Clock.System.now()

    "DocumentCreated round-trip" {
        val event: DocumentEvent = DocumentCreated(
            documentId = DocumentId("doc:test123"),
            name = "oauth2-design.md",
            mimeType = "text/markdown",
            s3Key = "efforts/effort:test123/docs/doc:test123/oauth2-design.md",
            createdBy = CollaboratorName.Individual("lead-dev"),
            createdAt = now,
        )
        val encoded = json.encodeToString<DocumentEvent>(event)
        val decoded = json.decodeFromString<DocumentEvent>(encoded)
        decoded shouldBe event
    }

    "DocumentUpdated round-trip" {
        val event: DocumentEvent = DocumentUpdated(
            documentId = DocumentId("doc:test123"),
            s3Key = "efforts/effort:test123/docs/doc:test123/oauth2-design.md",
            updatedBy = CollaboratorName.Individual("lead-dev"),
            updatedAt = now,
        )
        val encoded = json.encodeToString<DocumentEvent>(event)
        val decoded = json.decodeFromString<DocumentEvent>(encoded)
        decoded shouldBe event
    }

    "DocumentDeleted round-trip" {
        val event: DocumentEvent = DocumentDeleted(
            documentId = DocumentId("doc:test123"),
            deletedBy = CollaboratorName.Individual("lead-dev"),
            deletedAt = now,
        )
        val encoded = json.encodeToString<DocumentEvent>(event)
        val decoded = json.decodeFromString<DocumentEvent>(encoded)
        decoded shouldBe event
    }
})
