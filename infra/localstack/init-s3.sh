#!/bin/bash
# ---------------------------------------------------------------------------
# LocalStack init hook — runs once when LocalStack is ready.
# Creates the S3 bucket used by the Document aggregate.
# ---------------------------------------------------------------------------
set -euo pipefail

echo "Creating S3 bucket: bigboote-documents"
awslocal s3 mb s3://bigboote-documents --region us-east-1
echo "S3 bucket created successfully."
