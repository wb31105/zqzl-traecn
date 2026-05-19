#!/bin/bash

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

echo "========================================"
echo "  ZQZL 微服务群 - Docker 停止脚本"
echo "========================================"
echo ""

echo "停止所有服务..."
echo "----------------------------------------"
if command -v docker-compose &> /dev/null; then
    docker-compose down
else
    docker compose down
fi

echo ""
echo "========================================"
echo "  所有服务已停止！"
echo "========================================"
