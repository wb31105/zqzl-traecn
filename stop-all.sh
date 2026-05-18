#!/bin/bash

echo "========================================"
echo "  ZQZL 微服务群停止脚本"
echo "========================================"
echo ""

kill_port() {
    local port=$1
    local pid=$(lsof -ti :$port 2>/dev/null || true)
    if [ -n "$pid" ]; then
        echo "关闭端口 $port (PID: $pid)..."
        kill -9 $pid 2>/dev/null || true
        sleep 1
        echo "端口 $port 已关闭"
    else
        echo "端口 $port 未运行"
    fi
}

echo "正在关闭所有服务..."
echo "----------------------------------------"

cd "$(dirname "$0")/frontend/gateway"
chmod +x stop-nginx.sh 2>/dev/null || true
./stop-nginx.sh 2>/dev/null || true

kill_port 80
kill_port 8761
kill_port 9000
kill_port 8080
kill_port 8081
kill_port 3000
kill_port 3031

echo ""
echo "========================================"
echo "所有服务已停止"
echo "========================================"
