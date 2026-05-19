#!/bin/bash

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

echo "========================================"
echo "  构建 User Web 镜像"
echo "========================================"

docker build -f .deploy/docker/frontend/user-web.Dockerfile -t zqzl/user-web:latest .

echo ""
echo "User Web 镜像构建完成！"
echo "镜像名称: zqzl/user-web:latest"
echo "========================================"
