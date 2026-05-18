#!/bin/bash

set -e

PROJECT_ROOT="/Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn"

echo "========================================"
echo "  ZQZL 微服务群一键启动脚本"
echo "========================================"
echo ""

echo "[1/8] 关闭正在运行的服务..."
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

kill_port 8761
kill_port 9000
kill_port 8080
kill_port 8081
kill_port 3000
kill_port 3031
kill_port 80

echo ""
echo "[2/8] 编译架构项目 (zqzl-framework)..."
echo "----------------------------------------"
cd "$PROJECT_ROOT/backend/frameworks/zqzl-framework"
mvn clean install -DskipTests -q
echo "架构项目编译完成"

echo ""
echo "[3/8] 编译后端服务..."
echo "----------------------------------------"

echo "编译 Eureka 注册中心..."
cd "$PROJECT_ROOT/backend/services/eureka-server"
mvn clean package -DskipTests -q
echo "Eureka 注册中心编译完成"

echo "编译 Gateway 网关服务..."
cd "$PROJECT_ROOT/backend/services/gateway-server"
mvn clean package -DskipTests -q
echo "Gateway 网关服务编译完成"

echo "编译 SSO 后端服务..."
cd "$PROJECT_ROOT/backend/services/sso-server"
mvn clean package -DskipTests -q
echo "SSO 后端服务编译完成"

echo "编译 User 后端服务..."
cd "$PROJECT_ROOT/backend/services/user-server"
mvn clean package -DskipTests -q
echo "User 后端服务编译完成"

echo ""
echo "[4/8] 安装前端依赖..."
echo "----------------------------------------"

echo "安装 SSO 前端依赖..."
cd "$PROJECT_ROOT/frontend/apps/sso-web"
npm install --prefer-offline -s 2>/dev/null || true

echo "安装 User 前端依赖..."
cd "$PROJECT_ROOT/frontend/apps/user-web"
npm install --prefer-offline -s 2>/dev/null || true

echo "前端依赖安装完成"

echo ""
echo "[5/8] 启动 Eureka 注册中心..."
echo "----------------------------------------"
cd "$PROJECT_ROOT/backend/services/eureka-server"
osascript -e 'tell application "Terminal" to do script "cd '"$PROJECT_ROOT"'/backend/services/eureka-server && mvn spring-boot:run"' 2>/dev/null || true
echo "Eureka 注册中心启动中...端口: 8761"
sleep 10

echo ""
echo "[6/8] 启动后端服务..."
echo "----------------------------------------"

cd "$PROJECT_ROOT/backend/services/user-server"
osascript -e 'tell application "Terminal" to do script "cd '"$PROJECT_ROOT"'/backend/services/user-server && mvn spring-boot:run"' 2>/dev/null || true
echo "User 后端服务启动中...端口: 8081"
sleep 5

cd "$PROJECT_ROOT/backend/services/sso-server"
osascript -e 'tell application "Terminal" to do script "cd '"$PROJECT_ROOT"'/backend/services/sso-server && mvn spring-boot:run"' 2>/dev/null || true
echo "SSO 后端服务启动中...端口: 8080"
sleep 5

cd "$PROJECT_ROOT/backend/services/gateway-server"
osascript -e 'tell application "Terminal" to do script "cd '"$PROJECT_ROOT"'/backend/services/gateway-server && mvn spring-boot:run"' 2>/dev/null || true
echo "Gateway 网关服务启动中...端口: 9000"
sleep 5

echo ""
echo "[7/8] 启动前端服务..."
echo "----------------------------------------"
cd "$PROJECT_ROOT/frontend/apps/sso-web"
osascript -e 'tell application "Terminal" to do script "cd '"$PROJECT_ROOT"'/frontend/apps/sso-web && npm start"' 2>/dev/null || true
echo "SSO 前端服务启动中...端口: 3000"

sleep 3

cd "$PROJECT_ROOT/frontend/apps/user-web"
osascript -e 'tell application "Terminal" to do script "cd '"$PROJECT_ROOT"'/frontend/apps/user-web && npm start"' 2>/dev/null || true
echo "User 前端服务启动中...端口: 3031"
sleep 3

echo ""
echo "[8/8] 启动 Nginx 前端网关..."
echo "----------------------------------------"
cd "$PROJECT_ROOT/frontend/gateway"
chmod +x start-nginx.sh
./start-nginx.sh &
sleep 2

echo ""
echo "========================================"
echo "  所有服务启动完成！"
echo "========================================"
echo "统一访问入口："
echo "  - 主页/登录:    http://localhost"
echo "  - 用户管理:     http://localhost/user-web/"
echo ""
echo "服务端口分配："
echo "  - Eureka 注册中心: 8761 (http://localhost:8761)"
echo "  - 后端 API 网关:   9000"
echo "  - User 后端服务:   8081"
echo "  - SSO 后端服务:    8080"
echo "  - SSO 前端:        3000"
echo "  - User 前端:       3031"
echo "  - Nginx 前端网关:  80"
echo ""
echo "默认账号：admin / admin123"
echo "========================================"
