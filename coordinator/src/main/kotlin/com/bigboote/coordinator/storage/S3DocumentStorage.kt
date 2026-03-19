package com.bigboote.coordinator.storage

import com.bigboote.domain.values.DocumentId
import com.bigboote.domain.values.EffortId
import com.bigboote.infra.config.S3Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.net.URI
import java.nio.charset.StandardCharsets

private val logger = LoggerFactory.getLogger(S3DocumentStorage::class.java)

/**
 * Abstraction over document content storage backed by S3 (or an S3-compatible service).
 *
 * Used by [com.bigboote.coordinator.aggregates.document.DocumentCommandHandler] to
 * persist document content before appending the corresponding domain event. Content
 * is stored as UTF-8 text under a deterministic S3 key derived from the effort and
 * document identifiers.
 *
 * The S3 key format is:  `efforts/{effortId}/docs/{docId}/{name}`
 * e.g. `efforts/effort:V1St/docs/doc:7/oauth2-design.md`
 *
 * See Architecture doc Section 14 and API Design doc Section 3.5.
 */
interface S3DocumentStorage {

    /**
     * Compute the canonical S3 key for a document.
     * Deterministic: given the same inputs, always returns the same key.
     */
    fun computeKey(effortId: EffortId, documentId: DocumentId, name: String): String =
        "efforts/${effortId.value}/docs/${documentId.value}/$name"

    /** Upload [content] (UTF-8 text) to [s3Key] in the configured bucket. */
    suspend fun put(s3Key: String, content: String, mimeType: String)

    /** Download and return the UTF-8 text content at [s3Key]. */
    suspend fun get(s3Key: String): String

    /**
     * Delete the object at [s3Key].
     * No-op if the key does not exist.
     */
    suspend fun delete(s3Key: String)
}

/**
 * AWS SDK v2 implementation of [S3DocumentStorage].
 *
 * Wraps the synchronous [S3Client] in `withContext(Dispatchers.IO)` to avoid
 * blocking the Ktor/coroutine dispatcher. Supports both real AWS and
 * S3-compatible local services (LocalStack, MinIO) via [S3Config.endpoint].
 *
 * **LocalStack / MinIO setup:**
 * Set `BIGBOOTE_S3_ENDPOINT=http://localhost:4566` (LocalStack) or
 * `http://localhost:9000` (MinIO) in your `.env` file. See the dev runbook
 * for the `docker-compose` snippet and bucket creation steps.
 */
class AwsS3DocumentStorage(private val config: S3Config) : S3DocumentStorage {

    private val client: S3Client = buildClient(config)

    // ---- interface implementation ----

    override suspend fun put(s3Key: String, content: String, mimeType: String) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        withContext(Dispatchers.IO) {
            client.putObject(
                PutObjectRequest.builder()
                    .bucket(config.bucket)
                    .key(s3Key)
                    .contentType(mimeType)
                    .contentLength(bytes.size.toLong())
                    .build(),
                RequestBody.fromBytes(bytes),
            )
        }
        logger.debug("S3: uploaded {} bytes to s3://{}/{}", bytes.size, config.bucket, s3Key)
    }

    override suspend fun get(s3Key: String): String = withContext(Dispatchers.IO) {
        client.getObjectAsBytes(
            GetObjectRequest.builder()
                .bucket(config.bucket)
                .key(s3Key)
                .build()
        ).asUtf8String()
    }

    override suspend fun delete(s3Key: String) {
        withContext(Dispatchers.IO) {
            try {
                client.deleteObject(
                    DeleteObjectRequest.builder()
                        .bucket(config.bucket)
                        .key(s3Key)
                        .build()
                )
                logger.debug("S3: deleted s3://{}/{}", config.bucket, s3Key)
            } catch (e: NoSuchKeyException) {
                logger.debug("S3: delete no-op — key not found: s3://{}/{}", config.bucket, s3Key)
            }
        }
    }

    // ---- private helpers ----

    private companion object {
        fun buildClient(config: S3Config): S3Client {
            val builder = S3Client.builder()
                .region(Region.of(config.region))

            // Custom endpoint: LocalStack or MinIO
            if (config.endpoint != null) {
                builder.endpointOverride(URI.create(config.endpoint))
                // Path-style access is required for LocalStack / MinIO
                builder.forcePathStyle(true)
            }

            // Static credentials (dev only); IAM role is preferred in production
            if (config.accessKeyId != null && config.secretAccessKey != null) {
                builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.accessKeyId, config.secretAccessKey)
                    )
                )
            } else {
                builder.credentialsProvider(DefaultCredentialsProvider.create())
            }

            return builder.build()
        }
    }
}
