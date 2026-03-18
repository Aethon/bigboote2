package com.bigboote.infra.config

/**
 * AWS S3 (or S3-compatible) storage settings used by the Document aggregate (Phase 14).
 *
 * For production, leave [endpoint] null and supply credentials via IAM role or the
 * standard credential chain. For local development with LocalStack / MinIO, set
 * [endpoint] to the local service URL and supply static access keys.
 *
 * Environment variables:
 * - `BIGBOOTE_S3_ENDPOINT`    — e.g. "http://localhost:4566" (LocalStack) or omit for real AWS
 * - `BIGBOOTE_S3_REGION`      — AWS region (default: "us-east-1")
 * - `BIGBOOTE_S3_BUCKET`      — S3 bucket name (default: "bigboote-documents")
 * - `BIGBOOTE_S3_ACCESS_KEY`  — static access key (optional; use IAM role in production)
 * - `BIGBOOTE_S3_SECRET_KEY`  — static secret key (optional; use IAM role in production)
 *
 * See Architecture doc Section 14 and dev runbook for LocalStack setup.
 */
data class S3Config(
    /** null = real AWS; "http://localhost:4566" = LocalStack; "http://localhost:9000" = MinIO */
    val endpoint: String?,
    val region: String,
    val bucket: String,
    /** null = use the default credential chain (IAM role, env vars, ~/.aws/credentials) */
    val accessKeyId: String?,
    val secretAccessKey: String?,
)
