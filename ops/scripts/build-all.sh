#!/bin/bash

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

echo "========================================"
echo "  构建所有服务镜像"
echo "========================================"

echo ""
echo "1. 构建 User Server..."
bash backend/services/user-server/deploy/build.sh

echo ""
echo "2. 构建 SSO Server..."
bash backend/services/sso-server/deploy/build.sh

echo ""
echo "3. 构建 SSO Web..."
bash frontend/apps/sso-web/deploy/build.sh

echo ""
echo "4. 构建 User Web..."
bash frontend/apps/user-web/deploy/build.sh

echo ""
echo "========================================"
echo "  所有服务镜像构建完成！"
echo "========================================"
