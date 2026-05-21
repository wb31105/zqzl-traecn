#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/../docker/apisix"

echo "========================================"
echo "  构建 APISIX 网关镜像"
echo "========================================"

docker build -t zqzl/apisix-gateway:latest .

echo ""
echo "APISIX 网关镜像构建完成！"
echo "镜像名称: zqzl/apisix-gateway:latest"
echo "========================================"
