#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
cd "$PROJECT_ROOT"

ACTION="${1:-start}"
MODULE="${2:-all}"

ENV_FILE="$PROJECT_ROOT/ops/env/local/.env.apisix"

echo "========================================"
echo "  ZQZL 本地启动脚本"
echo "========================================"
echo "项目根目录: $PROJECT_ROOT"
echo "操作: $ACTION"
echo "模块: $MODULE"
echo ""

load_gateway_env() {
    if [ -f "$1" ]; then
        set -a
        source "$1"
        set +a
    fi
}

check_port() {
    local port=$1
    if lsof -Pi :"$port" -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "端口 $port 已被占用"
        return 1
    fi
    return 0
}

open_new_terminal() {
    local command="$1"
    local window_name="$2"
    
    osascript <<EOF
tell application "Terminal"
    activate
    set newTab to do script "$command"
    set custom title of newTab to "$window_name"
    set current settings of newTab to settings set "Basic"
end tell
EOF
}

stop_apisix() {
    echo "停止 APISIX 网关..."
    if docker ps -q -f name="zqzl-apisix-local" >/dev/null 2>&1; then
        docker compose -f docker-compose-local.yml down 2>/dev/null || true
    fi
}

stop_process() {
    local pid_file="$1"
    local service_name="$2"
    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            echo "停止 $service_name (PID: $pid)..."
            kill "$pid" 2>/dev/null || true
            sleep 2
        fi
        rm -f "$pid_file"
    fi
}

stop_all_services() {
    echo ""
    echo "停止所有服务..."

    stop_apisix

    stop_process "$PROJECT_ROOT/.pid/user-server.pid" "user-server"
    stop_process "$PROJECT_ROOT/.pid/sso-server.pid" "sso-server"
    stop_process "$PROJECT_ROOT/.pid/sso-web.pid" "sso-web"
    stop_process "$PROJECT_ROOT/.pid/user-web.pid" "user-web"

    echo ""
    echo "所有服务已停止"
}

start_in_new_terminal() {
    local service_name="$1"
    local start_command="$2"
    local window_title="$3"

    echo "正在打开新终端启动 $service_name..."
    echo "  命令: $start_command"
    echo ""

    local full_command="cd '$PROJECT_ROOT' && clear && echo '========================================' && echo '  $window_title' && echo '========================================' && echo '' && $start_command"

    open_new_terminal "$full_command" "$window_title"

    sleep 2
    echo "✓ $service_name 已在新终端启动"
    echo ""
}

start_apisix() {
    echo "========================================"
    echo "  [1/5] 启动 APISIX 网关"
    echo "========================================"

    if ! check_port "8080"; then
        echo "警告: 端口 8080 已被占用，跳过 APISIX 启动"
        return 1
    fi

    echo "检查 APISIX 网关镜像..."
    if ! docker images zqzl/apisix-gateway:latest --format "{{.Repository}}" | grep -q "zqzl/apisix-gateway"; then
        echo "APISIX 网关镜像不存在，开始构建..."
        bash "$PROJECT_ROOT/ops/scripts/docker/build-service.sh" apisix
    fi

    echo "启动 APISIX 网关容器..."
    echo "  - 使用 docker compose 方式启动"
    echo "  - 监听端口: 8080 (映射到容器 9080)"
    echo "  - 使用 host.docker.internal 访问宿主机服务"
    echo ""

    docker compose -f docker-compose-local.yml up -d apisix

    sleep 3

    if docker ps -q -f name="zqzl-apisix-local" >/dev/null 2>&1; then
        echo "✓ APISIX 网关启动成功！"
        echo "  容器: zqzl-apisix-local"
    else
        echo "✗ APISIX 网关启动失败"
        docker compose -f docker-compose-local.yml logs 2>&1 | tail -20
        return 1
    fi

    echo ""
}

start_backend_service() {
    local service_name="$1"
    local http_port="$2"
    local grpc_port="$3"
    local index="$4"
    local total="$5"

    echo "========================================"
    echo "  [$index/$total] 启动 $service_name"
    echo "========================================"

    if ! check_port "$http_port"; then
        echo "警告: 端口 $http_port 已被占用，跳过 $service_name 启动"
        return 1
    fi

    if [ -n "$grpc_port" ] && ! check_port "$grpc_port"; then
        echo "警告: 端口 $grpc_port 已被占用，跳过 $service_name 启动"
        return 1
    fi

    JAR_FILE="$PROJECT_ROOT/backend/services/$service_name/target/$service_name-1.0.0.jar"
    if [ ! -f "$JAR_FILE" ]; then
        echo "$service_name jar 包不存在，先构建..."
        cd "$PROJECT_ROOT/backend/frameworks/zqzl-framework"
        mvn clean install -DskipTests
        cd "$PROJECT_ROOT/backend/services/$service_name"
        mvn clean package -DskipTests
        cd "$PROJECT_ROOT"
    fi

    START_SCRIPT="$PROJECT_ROOT/backend/services/$service_name/deploy/start.sh"
    WINDOW_TITLE="[$service_name] Spring Boot"
    START_CMD="bash '$START_SCRIPT' local"

    start_in_new_terminal "$service_name" "$START_CMD" "$WINDOW_TITLE"

    sleep 5

    if lsof -Pi :"$http_port" -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "✓ $service_name 启动成功！(端口: $http_port)"
    else
        echo "⚠ $service_name 正在启动中，请查看对应终端..."
    fi

    echo ""
}

start_frontend_app() {
    local app_name="$1"
    local index="$2"
    local total="$3"

    echo "========================================"
    echo "  [$index/$total] 启动 $app_name"
    echo "========================================"

    cd "$PROJECT_ROOT/frontend/apps/$app_name"

    if [ ! -d "node_modules" ]; then
        echo "安装依赖..."
        npm install
    fi

    START_SCRIPT="$PROJECT_ROOT/frontend/apps/$app_name/deploy/start.sh"
    WINDOW_TITLE="[$app_name] React"
    START_CMD="cd '$PROJECT_ROOT/frontend/apps/$app_name' && bash '$START_SCRIPT' local"

    start_in_new_terminal "$app_name" "$START_CMD" "$WINDOW_TITLE"

    sleep 3
    echo "✓ $app_name 已启动，请查看对应终端..."
    echo ""
}

start_service() {
    local service="$1"
    case "$service" in
        apisix)
            start_apisix
            ;;
        user-server)
            start_backend_service "user-server" "8081" "9091" "2" "5"
            ;;
        sso-server)
            start_backend_service "sso-server" "8080" "9090" "3" "5"
            ;;
        sso-web)
            start_frontend_app "sso-web" "4" "5"
            ;;
        user-web)
            start_frontend_app "user-web" "5" "5"
            ;;
    esac
}

show_status() {
    load_gateway_env "$ENV_FILE"

    echo "========================================"
    echo "  服务状态"
    echo "========================================"
    echo ""

    echo "APISIX 网关:"
    if docker ps -q -f name="zqzl-apisix-local" >/dev/null 2>&1; then
        echo "  ✅ 运行中 (端口: 8080)"
    else
        echo "  ❌ 未运行"
    fi

    echo ""
    echo "后端服务:"
    if lsof -Pi :8081 -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "  ✅ user-server - 端口: 8081"
    else
        echo "  ❌ user-server - 未运行"
    fi

    if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "  ✅ sso-server - 端口: 8080"
    else
        echo "  ❌ sso-server - 未运行"
    fi

    echo ""
    echo "前端应用:"
    if lsof -Pi :3001 -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "  ✅ sso-web - 端口: 3001"
    else
        echo "  ❌ sso-web - 未运行"
    fi

    if lsof -Pi :3002 -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "  ✅ user-web - 端口: 3002"
    else
        echo "  ❌ user-web - 未运行"
    fi

    echo ""
    echo "网关访问地址（需配置 hosts）:"
    echo "  登录门户: http://${SSO_WEB_HOST}:8080"
    echo "  管理平台: http://${ADMIN_WEB_HOST}:8080"
    echo "  API 网关: http://${API_HOST}:8080"
    echo ""
    echo "Hosts 配置:"
    echo "  127.0.0.1 ${SSO_WEB_HOST} ${ADMIN_WEB_HOST} ${API_HOST}"
    echo ""
    echo "默认账号: admin / admin123"
    echo ""
}

show_help() {
    echo "用法: $0 [操作] [模块]"
    echo ""
    echo "操作:"
    echo "  start     启动服务（默认）"
    echo "  stop      停止服务"
    echo "  restart   重启服务"
    echo "  status    查看服务状态"
    echo "  help      显示帮助信息"
    echo ""
    echo "模块:"
    echo "  all          启动所有服务（默认）"
    echo "  apisix       仅启动 APISIX 网关"
    echo "  backend      启动所有后端服务（不含网关）"
    echo "  frontend     启动所有前端应用"
    echo "  sso-server   仅启动 sso-server"
    echo "  user-server  仅启动 user-server"
    echo "  sso-web      仅启动 sso-web"
    echo "  user-web     仅启动 user-web"
    echo ""
    echo "示例:"
    echo "  $0                          # 启动所有服务（各服务独立终端）"
    echo "  $0 start apisix             # 仅启动 APISIX 网关"
    echo "  $0 start backend            # 仅启动后端服务"
    echo "  $0 stop                     # 停止所有服务"
    echo "  $0 restart                  # 重启所有服务"
    echo "  $0 status                   # 查看服务状态"
    echo ""
    echo "说明:"
    echo "  - 后端服务使用 Spring Boot local profile (application-local.yml)"
    echo "  - 前端应用自动加载 .env.local 配置"
    echo "  - 网关使用 Docker，通过 host.docker.internal 代理到本机"
    echo "  - 每个服务在独立的终端窗口启动，可独立查看日志"
    echo "  - 网关服务后台运行，其他服务在独立终端前台运行"
    echo ""
}

if [ "$ACTION" = "help" ] || [ "$ACTION" = "--help" ] || [ "$ACTION" = "-h" ]; then
    show_help
    exit 0
fi

mkdir -p "$PROJECT_ROOT/.pid" "$PROJECT_ROOT/logs/local"

case "$ACTION" in
    start)
        case "$MODULE" in
            all)
                echo "将按顺序启动服务，每个服务在独立终端窗口运行..."
                echo ""
                start_apisix
                start_backend_service "user-server" "8081" "9091" "2" "5"
                start_backend_service "sso-server" "8080" "9090" "3" "5"
                start_frontend_app "sso-web" "4" "5"
                start_frontend_app "user-web" "5" "5"
                sleep 5
                show_status
                echo "========================================"
                echo "  所有服务启动完成！"
                echo "========================================"
                echo ""
                echo "请查看各终端窗口查看服务日志。"
                echo ""
                ;;
            apisix)
                start_apisix
                show_status
                ;;
            backend)
                start_backend_service "user-server" "8081" "9091" "1" "2"
                start_backend_service "sso-server" "8080" "9090" "2" "2"
                sleep 5
                show_status
                ;;
            frontend)
                start_frontend_app "sso-web" "1" "2"
                start_frontend_app "user-web" "2" "2"
                sleep 3
                show_status
                ;;
            sso-server|user-server|sso-web|user-web)
                start_service "$MODULE"
                sleep 3
                show_status
                ;;
            *)
                echo "错误: 不支持的模块: $MODULE"
                echo ""
                show_help
                exit 1
                ;;
        esac
        ;;

    stop)
        stop_all_services
        show_status
        ;;

    restart)
        stop_all_services
        sleep 2
        case "$MODULE" in
            all)
                start_apisix
                start_backend_service "user-server" "8081" "9091" "2" "5"
                start_backend_service "sso-server" "8080" "9090" "3" "5"
                start_frontend_app "sso-web" "4" "5"
                start_frontend_app "user-web" "5" "5"
                ;;
            *)
                start_service "$MODULE"
                ;;
        esac
        sleep 5
        show_status
        ;;

    status|ps)
        show_status
        ;;

    *)
        echo "错误: 不支持的操作: $ACTION"
        echo ""
        show_help
        exit 1
        ;;
esac
