#!/bin/bash

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

echo "========================================"
echo "  构建 SSO Web 镜像"
echo "========================================"

docker build -f .deploy/docker/frontend/sso-web.Dockerfile -t zqzl/sso-web:latest .

echo ""
echo "SSO Web 镜像构建完成！"
echo "镜像名称: zqzl/sso-web:latest"
echo "========================================"
