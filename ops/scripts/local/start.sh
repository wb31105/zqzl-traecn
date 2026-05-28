#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
cd "$PROJECT_ROOT"

ACTION="${1:-start}"
MODULE="${2:-all}"
FOREGROUND="${3:-true}"

ENV_FILE="$PROJECT_ROOT/ops/env/local/.env.apisix"
PID_DIR="$PROJECT_ROOT/.pid"
LOG_DIR="$PROJECT_ROOT/logs/local"

echo "========================================"
echo "  ZQZL 本地模式启动脚本"
echo "========================================"
echo "项目根目录: $PROJECT_ROOT"
echo "操作: $ACTION"
echo "模块: $MODULE"
echo "前台模式: $FOREGROUND"
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

get_pid_by_port() {
    local port=$1
    lsof -Pi :"$port" -sTCP:LISTEN -t 2>/dev/null | head -1
}

stop_apisix() {
    echo "停止 APISIX 网关..."
    if docker ps -q -f name="zqzl-apisix-local" >/dev/null 2>&1; then
        docker compose -f docker-compose-local.yml down 2>/dev/null || true
    fi
}

stop_process_by_pid() {
    local pid_file="$1"
    local service_name="$2"
    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            echo "停止 $service_name (PID: $pid)..."
            kill "$pid" 2>/dev/null || true
            sleep 2
            if kill -0 "$pid" 2>/dev/null; then
                echo "强制停止 $service_name (PID: $pid)..."
                kill -9 "$pid" 2>/dev/null || true
            fi
        fi
        rm -f "$pid_file"
    fi
}

stop_process_by_port() {
    local port="$1"
    local service_name="$2"
    local pid=$(get_pid_by_port "$port")
    if [ -n "$pid" ]; then
        echo "停止 $service_name (PID: $pid, 端口: $port)..."
        kill "$pid" 2>/dev/null || true
        sleep 2
        if kill -0 "$pid" 2>/dev/null; then
            echo "强制停止 $service_name (PID: $pid)..."
            kill -9 "$pid" 2>/dev/null || true
        fi
    fi
}

stop_all_services() {
    echo ""
    echo "停止所有服务..."

    stop_apisix

    stop_process_by_port "3002" "user-web"
    stop_process_by_port "3001" "sso-web"
    stop_process_by_port "8082" "user-server"
    stop_process_by_port "8081" "sso-server"

    rm -f "$PID_DIR"/*.pid

    echo ""
    echo "所有服务已停止"
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

    docker compose --env-file "$ENV_FILE" -f docker-compose-local.yml up -d apisix

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
    LOG_FILE="$LOG_DIR/$service_name.log"
    PID_FILE="$PID_DIR/$service_name.pid"

    echo "启动 $service_name..."
    echo "  JAR文件: $JAR_FILE"
    echo "  HTTP端口: $http_port"
    echo "  GRPC端口: $grpc_port"
    echo "  日志文件: $LOG_FILE"
    echo "  PID文件: $PID_FILE"
    echo ""

    if [ "$FOREGROUND" = "true" ]; then
        bash "$START_SCRIPT" local
    else
        nohup bash "$START_SCRIPT" local > "$LOG_FILE" 2>&1 &
        local pid=$!
        echo $pid > "$PID_FILE"
        sleep 5

        if lsof -Pi :"$http_port" -sTCP:LISTEN -t >/dev/null 2>&1; then
            echo "✓ $service_name 启动成功！(PID: $pid, 端口: $http_port)"
        else
            echo "⚠ $service_name 正在启动中，请查看日志: $LOG_FILE"
        fi
    fi

    echo ""
}

start_frontend_app() {
    local app_name="$1"
    local port="$2"
    local index="$3"
    local total="$4"

    echo "========================================"
    echo "  [$index/$total] 启动 $app_name"
    echo "========================================"

    if ! check_port "$port"; then
        echo "警告: 端口 $port 已被占用，跳过 $app_name 启动"
        return 1
    fi

    cd "$PROJECT_ROOT/frontend/apps/$app_name"

    if [ ! -d "node_modules" ]; then
        echo "安装依赖..."
        npm install
    fi

    START_SCRIPT="$PROJECT_ROOT/frontend/apps/$app_name/deploy/start.sh"
    LOG_FILE="$LOG_DIR/$app_name.log"
    PID_FILE="$PID_DIR/$app_name.pid"

    echo "启动 $app_name..."
    echo "  端口: $port"
    echo "  日志文件: $LOG_FILE"
    echo "  PID文件: $PID_FILE"
    echo ""

    if [ "$FOREGROUND" = "true" ]; then
        bash "$START_SCRIPT" local
    else
        nohup bash "$START_SCRIPT" local > "$LOG_FILE" 2>&1 &
        local pid=$!
        echo $pid > "$PID_FILE"
        sleep 5

        if lsof -Pi :"$port" -sTCP:LISTEN -t >/dev/null 2>&1; then
            echo "✓ $app_name 启动成功！(PID: $pid, 端口: $port)"
        else
            echo "⚠ $app_name 正在启动中，请查看日志: $LOG_FILE"
        fi
    fi

    echo ""
}

start_service() {
    local service="$1"
    case "$service" in
        apisix)
            start_apisix
            ;;
        user-server)
            start_backend_service "user-server" "8082" "9092" "2" "5"
            ;;
        sso-server)
            start_backend_service "sso-server" "8081" "9091" "3" "5"
            ;;
        sso-web)
            start_frontend_app "sso-web" "3001" "4" "5"
            ;;
        user-web)
            start_frontend_app "user-web" "3002" "5" "5"
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
    if lsof -Pi :8082 -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "  ✅ user-server - 端口: 8082"
    else
        echo "  ❌ user-server - 未运行"
    fi

    if lsof -Pi :8081 -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "  ✅ sso-server - 端口: 8081"
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
    echo "用法: $0 [操作] [模块] [前台模式]"
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
    echo "前台模式:"
    echo "  true   前台启动（默认，最后一个服务占据前台）"
    echo "  false  后台启动（所有服务后台运行，日志写入文件）"
    echo ""
    echo "示例:"
    echo "  $0                          # 启动所有服务（前台模式）"
    echo "  $0 start all false          # 启动所有服务（后台模式）"
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
    echo "  - 后台模式日志路径: logs/local/"
    echo ""
}

if [ "$ACTION" = "help" ] || [ "$ACTION" = "--help" ] || [ "$ACTION" = "-h" ]; then
    show_help
    exit 0
fi

mkdir -p "$PID_DIR" "$LOG_DIR"

case "$ACTION" in
    start)
        case "$MODULE" in
            all)
                if [ "$FOREGROUND" = "true" ]; then
                    echo "将按顺序启动服务，最后一个服务占据前台..."
                    echo "使用 Ctrl+C 停止当前前台服务"
                    echo "如需后台启动，请使用: $0 start all false"
                    echo ""
                    start_apisix
                    start_backend_service "user-server" "8082" "9092" "2" "5"
                    start_backend_service "sso-server" "8081" "9091" "3" "5"
                    start_frontend_app "sso-web" "3001" "4" "5"
                    start_frontend_app "user-web" "3002" "5" "5"
                else
                    echo "将按顺序启动所有服务，全部后台运行..."
                    echo "日志文件路径: $LOG_DIR/"
                    echo ""
                    start_apisix
                    start_backend_service "user-server" "8082" "9092" "2" "5"
                    start_backend_service "sso-server" "8081" "9091" "3" "5"
                    start_frontend_app "sso-web" "3001" "4" "5"
                    start_frontend_app "user-web" "3002" "5" "5"
                    sleep 5
                    show_status
                    echo "========================================"
                    echo "  所有服务启动完成！"
                    echo "========================================"
                    echo ""
                    echo "查看日志: tail -f $LOG_DIR/*.log"
                    echo ""
                fi
                ;;
            apisix)
                start_apisix
                show_status
                ;;
            backend)
                start_backend_service "user-server" "8082" "9092" "1" "2"
            start_backend_service "sso-server" "8081" "9091" "2" "2"
                sleep 5
                show_status
                ;;
            frontend)
                start_frontend_app "sso-web" "3001" "1" "2"
                start_frontend_app "user-web" "3002" "2" "2"
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
                start_backend_service "user-server" "8082" "9092" "2" "5"
                start_backend_service "sso-server" "8081" "9091" "3" "5"
                start_frontend_app "sso-web" "3001" "4" "5"
                start_frontend_app "user-web" "3002" "5" "5"
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
