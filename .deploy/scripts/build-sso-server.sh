#!/bin/bash

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

echo "========================================"
echo "  构建 SSO Server 镜像"
echo "========================================"

docker build -f .deploy/docker/backend/sso-server.Dockerfile -t zqzl/sso-server:latest .

echo ""
echo "SSO Server 镜像构建完成！"
echo "镜像名称: zqzl/sso-server:latest"
echo "========================================"
