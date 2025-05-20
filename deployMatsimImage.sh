#!/bin/bash
set -euo pipefail

TAG_NAME="matsim-v0.0.1"
ACCOUNT_NAME="xxxxxxxxxxxx"
REGION="eu-central-1"
REPO="${ACCOUNT_NAME}.dkr.ecr.${REGION}.amazonaws.com/matsim-jobs-repo"

# Build the image
podman build ./docker-build -t ${REPO}:${TAG_NAME} --platform linux/arm64

# Log in to ECR (only once)
aws ecr get-login-password --region ${REGION} | podman login --username AWS --password-stdin ${REPO}

# Push the image (from host, not inside podman machine)
podman push ${REPO}:${TAG_NAME}
