#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

IMAGE_TAG="${1:-latest}"
IMAGE_PREFIX="${2:-zqzl}"

bash "$SCRIPT_DIR/build-service.sh" all "$IMAGE_TAG" "$IMAGE_PREFIX"
