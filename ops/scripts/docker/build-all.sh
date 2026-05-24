#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "========================================"
echo "  构建所有服务镜像"
echo "========================================"

echo ""
echo "[1/4] 构建 APISIX 网关..."
bash "$SCRIPT_DIR/build-apisix.sh"

echo ""
echo "[2/4] 构建后端服务..."
bash "$SCRIPT_DIR/build-backend.sh"

echo ""
echo "[3/4] 构建前端应用..."
bash "$SCRIPT_DIR/build-frontend.sh"

echo ""
echo "========================================"
echo "  所有服务镜像构建完成！"
echo "========================================"
echo ""
echo "使用以下命令启动服务："
echo "  docker compose up -d"
echo ""
