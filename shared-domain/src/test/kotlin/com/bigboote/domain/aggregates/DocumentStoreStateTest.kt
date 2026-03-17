package com.bigboote.domain.aggregates

import com.bigboote.domain.events.DocumentEvent.*
import com.bigboote.domain.values.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock

class DocumentStoreStateTest : StringSpec({

    val now = Clock.System.now()
    val effortId = EffortId("effort:test123")
    val docId = DocumentId("doc:test123")

    val createdEvent = DocumentCreated(
        documentId = docId,
        effortId = effortId,
        name = "oauth2-design.md",
        mimeType = "text/markdown",
        s3Key = "efforts/effort:test123/docs/doc:test123/oauth2-design.md",
        createdBy = CollaboratorName.Individual("lead-dev"),
        createdAt = now,
    )

    "apply DocumentCreated adds record" {
        val state = DocumentStoreState.empty(effortId).apply(createdEvent)
        state.documents.size shouldBe 1
        val doc = state.documents[docId]!!
        doc.name shouldBe "oauth2-design.md"
        doc.mimeType shouldBe "text/markdown"
        doc.deleted shouldBe false
    }

    "apply DocumentUpdated updates s3Key" {
        val newS3Key = "efforts/effort:test123/docs/doc:test123/oauth2-design-v2.md"
        val state = DocumentStoreState.empty(effortId)
            .apply(createdEvent)
            .apply(DocumentUpdated(docId, effortId, newS3Key, CollaboratorName.Individual("lead-dev"), now))
        state.documents[docId]!!.s3Key shouldBe newS3Key
    }

    "apply DocumentDeleted marks as deleted" {
        val state = DocumentStoreState.empty(effortId)
            .apply(createdEvent)
            .apply(DocumentDeleted(docId, effortId, CollaboratorName.Individual("lead-dev"), now))
        state.documents[docId]!!.deleted shouldBe true
        // Document still in map (soft delete)
        state.documents.size shouldBe 1
    }

    "apply DocumentUpdated for unknown doc is no-op" {
        val state = DocumentStoreState.empty(effortId)
            .apply(DocumentUpdated(docId, effortId, "some-key", CollaboratorName.Individual("lead-dev"), now))
        state.documents.size shouldBe 0
    }

    "apply DocumentDeleted for unknown doc is no-op" {
        val state = DocumentStoreState.empty(effortId)
            .apply(DocumentDeleted(docId, effortId, CollaboratorName.Individual("lead-dev"), now))
        state.documents.size shouldBe 0
    }

    "multiple documents tracked independently" {
        val docId2 = DocumentId("doc:test456")
        val state = DocumentStoreState.empty(effortId)
            .apply(createdEvent)
            .apply(DocumentCreated(docId2, effortId, "readme.md", "text/markdown", "key2", CollaboratorName.Individual("alice"), now))
            .apply(DocumentDeleted(docId, effortId, CollaboratorName.Individual("lead-dev"), now))
        state.documents.size shouldBe 2
        state.documents[docId]!!.deleted shouldBe true
        state.documents[docId2]!!.deleted shouldBe false
    }
})
