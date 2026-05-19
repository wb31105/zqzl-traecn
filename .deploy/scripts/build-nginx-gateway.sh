#!/bin/bash

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

echo "========================================"
echo "  构建 Nginx Gateway 镜像"
echo "========================================"

docker build -f .deploy/docker/nginx/nginx.Dockerfile -t zqzl/nginx-gateway:latest .

echo ""
echo "Nginx Gateway 镜像构建完成！"
echo "镜像名称: zqzl/nginx-gateway:latest"
echo "========================================"
