package com.bigboote.coordinator.api.public.v1

import com.bigboote.coordinator.aggregates.document.DocumentCommandHandler
import com.bigboote.coordinator.api.error.DomainException
import com.bigboote.coordinator.api.error.ValidationException
import com.bigboote.coordinator.api.public.v1.dto.*
import com.bigboote.coordinator.auth.UserPrincipal
import com.bigboote.coordinator.projections.DocumentListProjection
import com.bigboote.coordinator.projections.repositories.DocumentReadRepository
import com.bigboote.coordinator.projections.repositories.DocumentRow
import com.bigboote.coordinator.storage.S3DocumentStorage
import com.bigboote.domain.commands.DocumentCommand.*
import com.bigboote.domain.errors.DomainError
import com.bigboote.domain.values.CollaboratorName
import com.bigboote.domain.values.DocumentId
import com.bigboote.domain.values.EffortId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.bigboote.coordinator.api.public.v1.DocumentRoutes")

/**
 * Document API endpoints mounted under /api/v1/efforts/{effortId}/documents.
 *
 * GET  /api/v1/efforts/{effortId}/documents
 *      → list all non-deleted documents; returns 200 with [DocumentListResponse].
 *
 * GET  /api/v1/efforts/{effortId}/documents/{docId}
 *      → get document metadata + content; returns 200 with [DocumentDetailResponse].
 *
 * POST /api/v1/efforts/{effortId}/documents/create
 *      → CreateDocument command; returns 201 with [CreateDocumentResponse].
 *
 * POST /api/v1/efforts/{effortId}/documents/{docId}/update
 *      → UpdateDocument command; returns 200 with [DocumentSummaryResponse].
 *
 * POST /api/v1/efforts/{effortId}/documents/{docId}/delete
 *      → DeleteDocument command; returns 200 with [DeleteDocumentResponse].
 *
 * See API Design doc Section 3.5.
 */
fun Route.documentRoutes() {
    val commandHandler by inject<DocumentCommandHandler>()
    val projection     by inject<DocumentListProjection>()
    val readRepo       by inject<DocumentReadRepository>()
    val s3             by inject<S3DocumentStorage>()

    route("/efforts/{effortId}/documents") {

        // ------------------------------------------------------------------
        // POST /api/v1/efforts/{effortId}/documents/create
        // ------------------------------------------------------------------
        post("/create") {
            val effortId = parseDocEffortIdParam(call)
            val principal = call.principal<UserPrincipal>()
                ?: throw ValidationException("Authentication required")
            val req = call.receive<CreateDocumentRequest>()
            validateCreateDocumentRequest(req)

            val createdBy: CollaboratorName = try {
                CollaboratorName.from(principal.collaboratorName)
            } catch (e: IllegalArgumentException) {
                throw ValidationException("Invalid collaborator name: ${e.message}")
            }

            val documentId = DocumentId.generate()
            val s3Key = s3.computeKey(effortId, documentId, req.name.trim())

            val cmd = CreateDocument(
                documentId = documentId,
                effortId   = effortId,
                name       = req.name.trim(),
                mimeType   = req.mimeType.trim(),
                s3Key      = s3Key,
                createdBy  = createdBy,
            )

            commandHandler.handle(cmd, req.content)

            // Begin tracking the docs stream for this effort (idempotent)
            projection.trackEffort(effortId)
            // Project event directly for read-your-writes consistency
            projection.project(
                com.bigboote.domain.events.DocumentEvent.DocumentCreated(
                    documentId = documentId,
                    effortId   = effortId,
                    name       = req.name.trim(),
                    mimeType   = req.mimeType.trim(),
                    s3Key      = s3Key,
                    createdBy  = createdBy,
                    createdAt  = kotlinx.datetime.Clock.System.now(),
                )
            )

            logger.info("Document created via API: {} in effort {}", documentId, effortId)

            call.respond(
                HttpStatusCode.Created,
                CreateDocumentResponse(
                    docId     = documentId.value,
                    name      = req.name.trim(),
                    s3Key     = s3Key,
                    createdAt = kotlinx.datetime.Clock.System.now().toString(),
                    createdBy = createdBy.toString(),
                )
            )
        }

        // ------------------------------------------------------------------
        // GET /api/v1/efforts/{effortId}/documents
        // ------------------------------------------------------------------
        get {
            val effortId = parseDocEffortIdParam(call)
            val documents = readRepo.list(effortId)
            call.respond(
                HttpStatusCode.OK,
                DocumentListResponse(documents = documents.map { it.toSummaryResponse() })
            )
        }

        // ------------------------------------------------------------------
        // GET /api/v1/efforts/{effortId}/documents/{docId}
        // ------------------------------------------------------------------
        get("/{docId}") {
            val effortId   = parseDocEffortIdParam(call)
            val documentId = parseDocIdParam(call)

            val row = readRepo.get(effortId, documentId)
                ?: throw DomainException(DomainError.DocumentNotFound(documentId))

            val content = s3.get(row.s3Key)

            call.respond(
                HttpStatusCode.OK,
                DocumentDetailResponse(
                    docId     = row.documentId.value,
                    effortId  = row.effortId.value,
                    name      = row.name,
                    mimeType  = row.mimeType,
                    s3Key     = row.s3Key,
                    content   = content,
                    createdAt = row.createdAt.toString(),
                    createdBy = row.createdBy,
                )
            )
        }

        // ------------------------------------------------------------------
        // POST /api/v1/efforts/{effortId}/documents/{docId}/update
        // ------------------------------------------------------------------
        post("/{docId}/update") {
            val effortId   = parseDocEffortIdParam(call)
            val documentId = parseDocIdParam(call)
            val principal  = call.principal<UserPrincipal>()
                ?: throw ValidationException("Authentication required")
            val req = call.receive<UpdateDocumentRequest>()

            val updatedBy: CollaboratorName = try {
                CollaboratorName.from(principal.collaboratorName)
            } catch (e: IllegalArgumentException) {
                throw ValidationException("Invalid collaborator name: ${e.message}")
            }

            val row = readRepo.get(effortId, documentId)
                ?: throw DomainException(DomainError.DocumentNotFound(documentId))

            val s3Key = s3.computeKey(effortId, documentId, row.name)

            val cmd = UpdateDocument(
                documentId = documentId,
                effortId   = effortId,
                s3Key      = s3Key,
                updatedBy  = updatedBy,
            )

            commandHandler.handle(cmd, req.content)

            // Project event directly for read-your-writes consistency
            projection.project(
                com.bigboote.domain.events.DocumentEvent.DocumentUpdated(
                    documentId = documentId,
                    effortId   = effortId,
                    s3Key      = s3Key,
                    updatedBy  = updatedBy,
                    updatedAt  = kotlinx.datetime.Clock.System.now(),
                )
            )

            logger.info("Document updated via API: {} in effort {}", documentId, effortId)

            call.respond(
                HttpStatusCode.OK,
                readRepo.get(effortId, documentId)?.toSummaryResponse()
                    ?: DocumentSummaryResponse(
                        docId     = documentId.value,
                        name      = row.name,
                        mimeType  = row.mimeType,
                        s3Key     = s3Key,
                        createdAt = row.createdAt.toString(),
                        createdBy = row.createdBy,
                    )
            )
        }

        // ------------------------------------------------------------------
        // POST /api/v1/efforts/{effortId}/documents/{docId}/delete
        // ------------------------------------------------------------------
        post("/{docId}/delete") {
            val effortId   = parseDocEffortIdParam(call)
            val documentId = parseDocIdParam(call)
            val principal  = call.principal<UserPrincipal>()
                ?: throw ValidationException("Authentication required")

            val deletedBy: CollaboratorName = try {
                CollaboratorName.from(principal.collaboratorName)
            } catch (e: IllegalArgumentException) {
                throw ValidationException("Invalid collaborator name: ${e.message}")
            }

            val cmd = DeleteDocument(
                documentId = documentId,
                effortId   = effortId,
                deletedBy  = deletedBy,
            )

            commandHandler.handle(cmd)

            // Project event directly for read-your-writes consistency
            projection.project(
                com.bigboote.domain.events.DocumentEvent.DocumentDeleted(
                    documentId = documentId,
                    effortId   = effortId,
                    deletedBy  = deletedBy,
                    deletedAt  = kotlinx.datetime.Clock.System.now(),
                )
            )

            logger.info("Document deleted via API: {} in effort {}", documentId, effortId)

            call.respond(
                HttpStatusCode.OK,
                DeleteDocumentResponse(docId = documentId.value)
            )
        }
    }
}

// ------------------------------------------------------------------ private helpers

private fun parseDocEffortIdParam(call: ApplicationCall): EffortId {
    val raw = call.parameters["effortId"]
    if (raw.isNullOrBlank()) throw ValidationException("Missing effortId path parameter")
    return try {
        EffortId(raw)
    } catch (e: IllegalArgumentException) {
        throw ValidationException("Invalid effortId: '$raw'")
    }
}

private fun parseDocIdParam(call: ApplicationCall): DocumentId {
    val raw = call.parameters["docId"]
    if (raw.isNullOrBlank()) throw ValidationException("Missing docId path parameter")
    return try {
        DocumentId(raw)
    } catch (e: IllegalArgumentException) {
        throw ValidationException("Invalid docId: '$raw'")
    }
}

private fun validateCreateDocumentRequest(req: CreateDocumentRequest) {
    if (req.name.isBlank())     throw ValidationException("'name' must not be blank")
    if (req.mimeType.isBlank()) throw ValidationException("'mimeType' must not be blank")
    if (req.content.isEmpty())  throw ValidationException("'content' must not be empty")
}

private fun DocumentRow.toSummaryResponse(): DocumentSummaryResponse =
    DocumentSummaryResponse(
        docId     = documentId.value,
        name      = name,
        mimeType  = mimeType,
        s3Key     = s3Key,
        createdAt = createdAt.toString(),
        createdBy = createdBy,
    )
