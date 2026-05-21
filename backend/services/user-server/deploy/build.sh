#!/bin/bash

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "$PROJECT_ROOT"

echo "========================================"
echo "  构建 User Server 镜像"
echo "========================================"

docker build -f backend/services/user-server/deploy/Dockerfile -t zqzl/user-server:latest .

echo ""
echo "User Server 镜像构建完成！"
echo "镜像名称: zqzl/user-server:latest"
echo "========================================"
