#!/bin/bash

set -e

PROJECT_ROOT="/Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn"

echo "========================================"
echo "  ZQZL 微服务群一键启动脚本"
echo "========================================"
echo ""

echo "[1/5] 关闭正在运行的服务..."
echo "----------------------------------------"

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

kill_port 8080
kill_port 8081
kill_port 3000
kill_port 3031

echo ""
echo "[2/5] 编译架构项目 (zqzl-framework)..."
echo "----------------------------------------"
cd "$PROJECT_ROOT/backend/frameworks/zqzl-framework"
mvn clean install -DskipTests -q
echo "架构项目编译完成"

echo ""
echo "[3/5] 编译后端服务..."
echo "----------------------------------------"

echo "编译 SSO 后端服务..."
cd "$PROJECT_ROOT/backend/services/sso-server"
mvn clean package -DskipTests -q
echo "SSO 后端服务编译完成"

echo "编译 User 后端服务..."
cd "$PROJECT_ROOT/backend/services/user-server"
mvn clean package -DskipTests -q
echo "User 后端服务编译完成"

echo ""
echo "[4/5] 安装前端依赖..."
echo "----------------------------------------"

echo "安装 SSO 前端依赖..."
cd "$PROJECT_ROOT/frontend/apps/sso-web"
npm install --prefer-offline -s 2>/dev/null || true

echo "安装 User 前端依赖..."
cd "$PROJECT_ROOT/frontend/apps/user-web"
npm install --prefer-offline -s 2>/dev/null || true

echo "前端依赖安装完成"

echo ""
echo "[5/5] 启动所有服务..."
echo "========================================"
echo "正在为每个服务创建独立终端..."
echo "端口分配："
echo "  - User 后端:  8081"
echo "  - SSO 后端:   8080"
echo "  - SSO 前端:   3000"
echo "  - User 前端:  3031"
echo ""
echo "请稍候...服务正在启动中..."
echo "========================================"
