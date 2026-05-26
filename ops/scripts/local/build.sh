#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
cd "$PROJECT_ROOT"

MODULE="${1:-all}"

echo "========================================"
echo "  ZQZL 本地编译脚本"
echo "========================================"
echo "项目根目录: $PROJECT_ROOT"
echo "编译模块: $MODULE"
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
    echo "  构建框架层 zqzl-framework"
    echo "========================================"
    cd "$PROJECT_ROOT/backend/frameworks/zqzl-framework"
    mvn clean install -DskipTests
    echo ""
    echo "框架层构建完成！"
    echo ""
}

build_backend_service() {
    local service_name="$1"
    
    echo "========================================"
    echo "  构建 $service_name"
    echo "========================================"
    
    cd "$PROJECT_ROOT/backend/services/$service_name"
    mvn clean package -DskipTests
    
    echo ""
    echo "$service_name 构建完成！"
    echo "JAR 文件: target/${service_name}-1.0.0.jar"
    echo ""
}

build_frontend_app() {
    local app_name="$1"
    
    echo "========================================"
    echo "  构建 $app_name"
    echo "========================================"
    
    cd "$PROJECT_ROOT/frontend/apps/$app_name"
    
    if [ ! -d "node_modules" ]; then
        echo "安装依赖..."
        npm install
    fi
    
    npm run build
    
    echo ""
    echo "$app_name 构建完成！"
    echo "构建产物: build/"
    echo ""
}

build_apisix() {
    echo "========================================"
    echo "  构建 APISIX 网关镜像"
    echo "========================================"
    bash "$PROJECT_ROOT/ops/scripts/docker/build-service.sh" apisix
}

show_summary() {
    echo "========================================"
    echo "  本地编译完成！"
    echo "========================================"
    echo ""
    echo "构建产物位置："
    
    if [ "$MODULE" = "all" ] || [ "$MODULE" = "backend" ] || [ "$MODULE" = "user-server" ]; then
        echo "  - user-server: backend/services/user-server/target/user-server-1.0.0.jar"
    fi
    
    if [ "$MODULE" = "all" ] || [ "$MODULE" = "backend" ] || [ "$MODULE" = "sso-server" ]; then
        echo "  - sso-server: backend/services/sso-server/target/sso-server-1.0.0.jar"
    fi
    
    if [ "$MODULE" = "all" ] || [ "$MODULE" = "frontend" ] || [ "$MODULE" = "sso-web" ]; then
        echo "  - sso-web: frontend/apps/sso-web/build/"
    fi
    
    if [ "$MODULE" = "all" ] || [ "$MODULE" = "frontend" ] || [ "$MODULE" = "user-web" ]; then
        echo "  - user-web: frontend/apps/user-web/build/"
    fi
    
    echo ""
    echo "使用以下命令启动服务："
    echo "  bash ops/scripts/local/start.sh"
    echo ""
}

show_help() {
    echo "用法: $0 [模块]"
    echo ""
    echo "模块:"
    echo "  all          编译所有模块（默认）"
    echo "  framework    仅编译框架层"
    echo "  backend      编译所有后端服务"
    echo "  frontend     编译所有前端应用"
    echo "  apisix       仅编译 APISIX 网关镜像"
    echo "  sso-server   仅编译 sso-server"
    echo "  user-server  仅编译 user-server"
    echo "  sso-web      仅编译 sso-web"
    echo "  user-web     仅编译 user-web"
    echo ""
    echo "示例:"
    echo "  $0                          # 编译所有模块"
    echo "  $0 backend                  # 仅编译后端服务"
    echo "  $0 frontend                 # 仅编译前端应用"
    echo "  $0 sso-server               # 仅编译 sso-server"
    echo "  $0 apisix                   # 仅编译 APISIX 网关"
    echo ""
}

if [ "$MODULE" = "help" ] || [ "$MODULE" = "--help" ] || [ "$MODULE" = "-h" ]; then
    show_help
    exit 0
fi

check_java_version
check_maven_version
check_node_version

case "$MODULE" in
    all)
        build_framework
        build_backend_service "user-server"
        build_backend_service "sso-server"
        build_frontend_app "sso-web"
        build_frontend_app "user-web"
        build_apisix
        show_summary
        ;;
    
    framework)
        build_framework
        ;;
    
    backend)
        build_framework
        build_backend_service "user-server"
        build_backend_service "sso-server"
        show_summary
        ;;
    
    frontend)
        build_frontend_app "sso-web"
        build_frontend_app "user-web"
        show_summary
        ;;
    
    apisix)
        build_apisix
        ;;
    
    sso-server)
        build_framework
        build_backend_service "sso-server"
        show_summary
        ;;
    
    user-server)
        build_framework
        build_backend_service "user-server"
        show_summary
        ;;
    
    sso-web)
        build_frontend_app "sso-web"
        show_summary
        ;;
    
    user-web)
        build_frontend_app "user-web"
        show_summary
        ;;
    
    *)
        echo "错误: 不支持的模块: $MODULE"
        echo ""
        show_help
        exit 1
        ;;
esac
