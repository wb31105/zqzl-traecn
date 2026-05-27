#!/bin/bash
set -e

MODE="${1:-docker}"

echo "========================================"
echo "  启动 APISIX 网关"
echo "========================================"
echo "模式: $MODE"
echo ""

if [ "$MODE" = "local" ]; then
    echo "本地模式（Docker Compose）"
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

    cd "$PROJECT_ROOT"
    echo "使用配置文件: docker-compose-local.yml"
    echo ""
    exec docker compose -f docker-compose-local.yml up apisix
else
    echo "Docker启动模式"
    echo "执行 docker-entrypoint.sh 初始化配置..."
    echo ""
    exec /usr/local/bin/docker-entrypoint.sh /usr/local/openresty/bin/openresty -p /usr/local/apisix -g 'daemon off;'
fi
