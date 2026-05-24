#!/bin/bash

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$PROJECT_ROOT"

LOG_DIR="$PROJECT_ROOT/logs/local"
PID_DIR="$PROJECT_ROOT/.pid"
mkdir -p "$LOG_DIR"
mkdir -p "$PID_DIR"

echo "========================================"
echo "  ZQZL 本地一键启动脚本"
echo "========================================"
echo ""
echo "项目根目录: $PROJECT_ROOT"
echo "日志目录: $LOG_DIR"
echo ""

check_port() {
    local port=$1
    if lsof -Pi :"$port" -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "端口 $port 已被占用"
        return 1
    fi
    return 0
}

kill_existing_process() {
    local pid_file=$1
    local service_name=$2
    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            echo "停止已存在的 $service_name (PID: $pid)..."
            kill "$pid" 2>/dev/null || true
            sleep 2
        fi
        rm -f "$pid_file"
    fi
}

stop_all_services() {
    echo ""
    echo "停止所有服务..."
    
    if docker ps -q -f name=zqzl-apisix-local >/dev/null 2>&1; then
        echo "停止 APISIX 网关..."
        docker stop zqzl-apisix-local 2>/dev/null || true
        docker rm zqzl-apisix-local 2>/dev/null || true
    fi
    
    kill_existing_process "$PID_DIR/user-server.pid" "user-server"
    kill_existing_process "$PID_DIR/sso-server.pid" "sso-server"
    kill_existing_process "$PID_DIR/sso-web.pid" "sso-web"
    kill_existing_process "$PID_DIR/user-web.pid" "user-web"
    
    echo "所有服务已停止"
}

start_apisix() {
    echo "========================================"
    echo "  [1/5] 启动 APISIX 网关"
    echo "========================================"
    
    if ! check_port 8080; then
        echo "警告: 端口 8080 已被占用，跳过 APISIX 启动"
        return 1
    fi
    
    echo "检查 APISIX 本地镜像..."
    if ! docker images zqzl/apisix-gateway:local --format "{{.Repository}}" | grep -q "zqzl/apisix-gateway"; then
        echo "APISIX 本地镜像不存在，开始构建..."
        bash "$PROJECT_ROOT/ops/scripts/docker/build-apisix.sh"
    fi
    
    echo "启动 APISIX 网关容器..."
    docker run -d \
        --name zqzl-apisix-local \
        --network host \
        --restart unless-stopped \
        zqzl/apisix-gateway:local
    
    sleep 3
    
    if docker ps -q -f name=zqzl-apisix-local >/dev/null 2>&1; then
        echo "APISIX 网关启动成功！"
        echo "  - HTTP: http://localhost:8080"
    else
        echo "APISIX 网关启动失败"
        docker logs zqzl-apisix-local 2>&1 | tail -20
    fi
    echo ""
}

start_user_server() {
    echo "========================================"
    echo "  [2/5] 启动 user-server"
    echo "========================================"
    
    if ! check_port 8082; then
        echo "警告: 端口 8082 已被占用，跳过 user-server 启动"
        return 1
    fi
    
    if ! check_port 9092; then
        echo "警告: 端口 9092 已被占用，跳过 user-server 启动"
        return 1
    fi
    
    JAR_FILE="$PROJECT_ROOT/backend/services/user-server/target/user-server-1.0.0.jar"
    if [ ! -f "$JAR_FILE" ]; then
        echo "user-server jar 包不存在，先构建..."
        cd "$PROJECT_ROOT/backend/services/user-server"
        mvn clean package -DskipTests
    fi
    
    echo "启动 user-server..."
    cd "$PROJECT_ROOT/backend/services/user-server"
    nohup java -jar "$JAR_FILE" --spring.profiles.active=local > "$LOG_DIR/user-server.log" 2>&1 &
    echo $! > "$PID_DIR/user-server.pid"
    
    sleep 10
    
    if kill -0 "$(cat "$PID_DIR/user-server.pid")" 2>/dev/null; then
        echo "user-server 启动成功！"
        echo "  - HTTP: http://localhost:8082"
        echo "  - gRPC: localhost:9092"
        echo "  - 日志: $LOG_DIR/user-server.log"
    else
        echo "user-server 启动失败"
        cat "$LOG_DIR/user-server.log" | tail -30
    fi
    echo ""
}

start_sso_server() {
    echo "========================================"
    echo "  [3/5] 启动 sso-server"
    echo "========================================"
    
    if ! check_port 8081; then
        echo "警告: 端口 8081 已被占用，跳过 sso-server 启动"
        return 1
    fi
    
    if ! check_port 9091; then
        echo "警告: 端口 9091 已被占用，跳过 sso-server 启动"
        return 1
    fi
    
    JAR_FILE="$PROJECT_ROOT/backend/services/sso-server/target/sso-server-1.0.0.jar"
    if [ ! -f "$JAR_FILE" ]; then
        echo "sso-server jar 包不存在，先构建..."
        cd "$PROJECT_ROOT/backend/services/sso-server"
        mvn clean package -DskipTests
    fi
    
    echo "启动 sso-server..."
    cd "$PROJECT_ROOT/backend/services/sso-server"
    nohup java -jar "$JAR_FILE" --spring.profiles.active=local > "$LOG_DIR/sso-server.log" 2>&1 &
    echo $! > "$PID_DIR/sso-server.pid"
    
    sleep 10
    
    if kill -0 "$(cat "$PID_DIR/sso-server.pid")" 2>/dev/null; then
        echo "sso-server 启动成功！"
        echo "  - HTTP: http://localhost:8081"
        echo "  - gRPC: localhost:9091"
        echo "  - 日志: $LOG_DIR/sso-server.log"
    else
        echo "sso-server 启动失败"
        cat "$LOG_DIR/sso-server.log" | tail -30
    fi
    echo ""
}

start_sso_web() {
    echo "========================================"
    echo "  [4/5] 启动 sso-web"
    echo "========================================"
    
    if ! check_port 3001; then
        echo "警告: 端口 3001 已被占用，跳过 sso-web 启动"
        return 1
    fi
    
    cd "$PROJECT_ROOT/frontend/apps/sso-web"
    
    if [ ! -d "node_modules" ]; then
        echo "安装依赖..."
        npm install
    fi
    
    echo "启动 sso-web..."
    nohup npm start > "$LOG_DIR/sso-web.log" 2>&1 &
    echo $! > "$PID_DIR/sso-web.pid"
    
    sleep 5
    
    if kill -0 "$(cat "$PID_DIR/sso-web.pid")" 2>/dev/null; then
        echo "sso-web 启动成功！"
        echo "  - 访问: http://localhost:3001"
        echo "  - 日志: $LOG_DIR/sso-web.log"
    else
        echo "sso-web 启动失败"
        cat "$LOG_DIR/sso-web.log" | tail -30
    fi
    echo ""
}

start_user_web() {
    echo "========================================"
    echo "  [5/5] 启动 user-web"
    echo "========================================"
    
    if ! check_port 3002; then
        echo "警告: 端口 3002 已被占用，跳过 user-web 启动"
        return 1
    fi
    
    cd "$PROJECT_ROOT/frontend/apps/user-web"
    
    if [ ! -d "node_modules" ]; then
        echo "安装依赖..."
        npm install
    fi
    
    echo "启动 user-web..."
    nohup npm start > "$LOG_DIR/user-web.log" 2>&1 &
    echo $! > "$PID_DIR/user-web.pid"
    
    sleep 5
    
    if kill -0 "$(cat "$PID_DIR/user-web.pid")" 2>/dev/null; then
        echo "user-web 启动成功！"
        echo "  - 访问: http://localhost:3002"
        echo "  - 日志: $LOG_DIR/user-web.log"
    else
        echo "user-web 启动失败"
        cat "$LOG_DIR/user-web.log" | tail -30
    fi
    echo ""
}

show_status() {
    echo "========================================"
    echo "  服务状态"
    echo "========================================"
    echo ""
    
    echo "APISIX 网关:"
    if docker ps -q -f name=zqzl-apisix-local >/dev/null 2>&1; then
        echo "  ✅ 运行中 - http://localhost:8080"
    else
        echo "  ❌ 未运行"
    fi
    
    echo ""
    echo "后端服务:"
    if [ -f "$PID_DIR/user-server.pid" ] && kill -0 "$(cat "$PID_DIR/user-server.pid")" 2>/dev/null; then
        echo "  ✅ user-server - http://localhost:8082 (PID: $(cat "$PID_DIR/user-server.pid"))"
    else
        echo "  ❌ user-server - 未运行"
    fi
    
    if [ -f "$PID_DIR/sso-server.pid" ] && kill -0 "$(cat "$PID_DIR/sso-server.pid")" 2>/dev/null; then
        echo "  ✅ sso-server - http://localhost:8081 (PID: $(cat "$PID_DIR/sso-server.pid"))"
    else
        echo "  ❌ sso-server - 未运行"
    fi
    
    echo ""
    echo "前端应用:"
    if [ -f "$PID_DIR/sso-web.pid" ] && kill -0 "$(cat "$PID_DIR/sso-web.pid")" 2>/dev/null; then
        echo "  ✅ sso-web - http://localhost:3001 (PID: $(cat "$PID_DIR/sso-web.pid"))"
    else
        echo "  ❌ sso-web - 未运行"
    fi
    
    if [ -f "$PID_DIR/user-web.pid" ] && kill -0 "$(cat "$PID_DIR/user-web.pid")" 2>/dev/null; then
        echo "  ✅ user-web - http://localhost:3002 (PID: $(cat "$PID_DIR/user-web.pid"))"
    else
        echo "  ❌ user-web - 未运行"
    fi
    
    echo ""
    echo "默认账号: admin / admin123"
    echo ""
}

show_help() {
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  start     启动所有服务（默认）"
    echo "  stop      停止所有服务"
    echo "  restart   重启所有服务"
    echo "  status    查看服务状态"
    echo "  help      显示帮助信息"
    echo ""
    echo "示例:"
    echo "  $0              # 启动所有服务"
    echo "  $0 stop         # 停止所有服务"
    echo "  $0 restart      # 重启所有服务"
    echo "  $0 status       # 查看服务状态"
    echo ""
}

case "${1:-start}" in
    stop)
        stop_all_services
        ;;
    restart)
        stop_all_services
        sleep 2
        start_apisix
        start_user_server
        start_sso_server
        start_sso_web
        start_user_web
        show_status
        ;;
    status)
        show_status
        ;;
    help|--help|-h)
        show_help
        ;;
    start)
        start_apisix
        start_user_server
        start_sso_server
        start_sso_web
        start_user_web
        show_status
        ;;
    *)
        echo "未知选项: $1"
        show_help
        exit 1
        ;;
esac
