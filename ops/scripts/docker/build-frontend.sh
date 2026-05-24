#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

cd "$PROJECT_ROOT"

echo "========================================"
echo "  构建前端应用镜像"
echo "========================================"

echo ""
echo "1. 构建 SSO Web..."
bash frontend/apps/sso-web/deploy/build.sh

echo ""
echo "2. 构建 User Web..."
bash frontend/apps/user-web/deploy/build.sh

echo ""
echo "========================================"
echo "  前端应用镜像构建完成！"
echo "========================================"
