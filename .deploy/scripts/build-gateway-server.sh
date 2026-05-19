#!/bin/bash

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

echo "========================================"
echo "  构建 Gateway Server 镜像"
echo "========================================"

docker build -f .deploy/docker/backend/gateway-server.Dockerfile -t zqzl/gateway-server:latest .

echo ""
echo "Gateway Server 镜像构建完成！"
echo "镜像名称: zqzl/gateway-server:latest"
echo "========================================"
