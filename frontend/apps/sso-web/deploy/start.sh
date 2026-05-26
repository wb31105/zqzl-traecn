#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

MODE="${1:-docker}"

echo "========================================"
echo "  启动 SSO Web"
echo "========================================"
echo "模式: $MODE"
echo ""

if [ "$MODE" = "local" ]; then
    echo "本地启动模式"
    cd "$PROJECT_DIR"

    if [ ! -d "node_modules" ]; then
        echo "安装依赖..."
        npm install
    fi

    echo "启动开发服务器（自动加载 .env.local）"
    echo ""
    exec npm start
else
    echo "Docker启动模式"
    echo "执行 docker-entrypoint.sh 初始化配置..."
    echo ""
    exec /usr/local/bin/docker-entrypoint.sh nginx -g "daemon off;"
fi
