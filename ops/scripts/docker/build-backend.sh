#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

cd "$PROJECT_ROOT"

echo "========================================"
echo "  构建后端服务镜像"
echo "========================================"

echo ""
echo "1. 构建 User Server..."
bash backend/services/user-server/deploy/build.sh

echo ""
echo "2. 构建 SSO Server..."
bash backend/services/sso-server/deploy/build.sh

echo ""
echo "========================================"
echo "  后端服务镜像构建完成！"
echo "========================================"
