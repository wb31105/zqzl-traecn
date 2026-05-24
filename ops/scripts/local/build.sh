#!/bin/bash

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$PROJECT_ROOT"

echo "========================================"
echo "  ZQZL 本地一键编译脚本"
echo "========================================"
echo ""
echo "项目根目录: $PROJECT_ROOT"
echo ""

check_java_version() {
    echo "检查 Java 版本..."
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F'"' '{print $2}')
    echo "当前 Java 版本: $JAVA_VERSION"
    if [[ "$JAVA_VERSION" != 17* ]]; then
        echo "警告: 建议使用 Java 17 版本"
    fi
    echo ""
}

check_maven_version() {
    echo "检查 Maven 版本..."
    MVN_VERSION=$(mvn -version 2>&1 | head -n 1)
    echo "当前 Maven 版本: $MVN_VERSION"
    echo ""
}

check_node_version() {
    echo "检查 Node 版本..."
    NODE_VERSION=$(node -v)
    NPM_VERSION=$(npm -v)
    echo "当前 Node 版本: $NODE_VERSION"
    echo "当前 npm 版本: $NPM_VERSION"
    echo ""
}

build_framework() {
    echo "========================================"
    echo "  [1/3] 构建框架层 zqzl-framework"
    echo "========================================"
    cd "$PROJECT_ROOT/backend/frameworks/zqzl-framework"
    mvn clean install -DskipTests
    echo ""
    echo "框架层构建完成！"
    echo ""
}

build_backend_services() {
    echo "========================================"
    echo "  [2/3] 构建后端服务"
    echo "========================================"
    
    echo ""
    echo "构建 user-server..."
    cd "$PROJECT_ROOT/backend/services/user-server"
    mvn clean package -DskipTests
    echo "user-server 构建完成！"
    
    echo ""
    echo "构建 sso-server..."
    cd "$PROJECT_ROOT/backend/services/sso-server"
    mvn clean package -DskipTests
    echo "sso-server 构建完成！"
    
    echo ""
    echo "所有后端服务构建完成！"
    echo ""
}

build_frontend_apps() {
    echo "========================================"
    echo "  [3/3] 构建前端应用"
    echo "========================================"
    
    echo ""
    echo "构建 sso-web..."
    cd "$PROJECT_ROOT/frontend/apps/sso-web"
    if [ ! -d "node_modules" ]; then
        npm install
    fi
    npm run build:local
    echo "sso-web 构建完成！"
    
    echo ""
    echo "构建 user-web..."
    cd "$PROJECT_ROOT/frontend/apps/user-web"
    if [ ! -d "node_modules" ]; then
        npm install
    fi
    npm run build:local
    echo "user-web 构建完成！"
    
    echo ""
    echo "所有前端应用构建完成！"
    echo ""
}

show_summary() {
    echo "========================================"
    echo "  本地编译完成！"
    echo "========================================"
    echo ""
    echo "构建产物位置："
    echo "  - user-server: backend/services/user-server/target/user-server-1.0.0.jar"
    echo "  - sso-server: backend/services/sso-server/target/sso-server-1.0.0.jar"
    echo "  - sso-web: frontend/apps/sso-web/build/"
    echo "  - user-web: frontend/apps/user-web/build/"
    echo ""
    echo "APISIX 网关镜像（如需）："
    echo "  bash ops/scripts/docker/build-apisix.sh"
    echo ""
    echo "使用以下命令启动服务："
    echo "  bash ops/scripts/local/start.sh"
    echo ""
}

echo ""
check_java_version
check_maven_version
check_node_version

build_framework
build_backend_services
build_frontend_apps

show_summary
