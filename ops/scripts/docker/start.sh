#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
cd "$PROJECT_ROOT"

ENV_DIR="$PROJECT_ROOT/ops/env"

usage() {
    echo "用法: $0 <环境类型> [环境配置] [操作] [选项]"
    echo ""
    echo "环境类型:"
    echo "  integration  集成环境（多厂商部署，全部服务 Docker 化）"
    echo "  local        本地环境（仅 APISIX 网关 Docker 化）"
    echo ""
    echo "集成环境配置 (integration):"
    echo "  default      使用默认配置 (.env.default)"
    echo "  <自定义>     使用自定义配置 (.env.<自定义>)"
    echo ""
    echo "操作:"
    echo "  up           启动服务（默认）"
    echo "  down         停止服务"
    echo "  restart      重启服务"
    echo "  logs [服务]  查看服务日志"
    echo "  status       查看服务状态"
    echo "  build        构建所有镜像"
    echo ""
    echo "选项:"
    echo "  -d           后台启动（默认）"
    echo "  --abort-on-container-exit  前台启动"
    echo ""
    echo "示例:"
    echo "  # 集成环境 - 默认配置"
    echo "  $0 integration default up"
    echo "  $0 integration default up --abort-on-container-exit"
    echo "  $0 integration default down"
    echo ""
    echo "  # 集成环境 - 自定义配置"
    echo "  $0 integration <自定义> up"
    echo "  $0 integration <自定义> logs apisix"
    echo ""
    echo "  # 本地环境 - 仅 APISIX 网关"
    echo "  $0 local apisix up"
    echo "  $0 local apisix down"
    echo ""
    echo "  # 构建镜像"
    echo "  $0 integration default build"
    echo ""
    exit 1
}

ENV_TYPE="${1:-}"
ENV_CONFIG="${2:-}"
ACTION="${3:-up}"
DETACH="${4:--d}"

if [ -z "$ENV_TYPE" ]; then
    usage
fi

if [ "$ENV_TYPE" = "integration" ]; then
    ENV_CONFIG="${ENV_CONFIG:-default}"
    ENV_FILE="$ENV_DIR/integration/.env.${ENV_CONFIG}"
    COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"
    SERVICES=""
elif [ "$ENV_TYPE" = "local" ]; then
    ENV_CONFIG="${ENV_CONFIG:-apisix}"
    ENV_FILE="$ENV_DIR/local/.env.${ENV_CONFIG}"
    COMPOSE_FILE="$PROJECT_ROOT/docker-compose-local.yml"
    SERVICES="apisix"
else
    echo "错误: 不支持的环境类型: $ENV_TYPE"
    echo ""
    usage
fi

if [ ! -f "$ENV_FILE" ]; then
    echo "错误: 环境配置文件不存在: $ENV_FILE"
    echo ""
    if [ "$ENV_TYPE" = "integration" ]; then
        echo "可用的集成环境配置:"
        ls "$ENV_DIR/integration/" | grep '^\.env\.' | sed 's/^\.env\./  - /'
    else
        echo "可用的本地环境配置:"
        ls "$ENV_DIR/local/" | grep '^\.env\.' | sed 's/^\.env\./  - /'
    fi
    echo ""
    exit 1
fi

if [ ! -f "$COMPOSE_FILE" ]; then
    echo "错误: Compose 配置文件不存在: $COMPOSE_FILE"
    exit 1
fi

load_env() {
    if [ -f "$1" ]; then
        export $(grep -v '^#' "$1" | xargs)
    fi
}

show_status() {
    load_env "$ENV_FILE"
    
    echo "========================================"
    echo "  服务状态"
    echo "========================================"
    echo "环境类型: $ENV_TYPE"
    echo "环境配置: $ENV_CONFIG"
    echo ""
    
    if [ "$ENV_TYPE" = "local" ]; then
        echo "APISIX 网关:"
        if docker ps -q -f name="${COMPOSE_PROJECT_NAME}-apisix-local" >/dev/null 2>&1; then
            echo "  ✅ 运行中"
            echo "  - HTTP 端口: ${APISIX_HTTP_PORT}"
            echo "  - HTTPS 端口: ${APISIX_HTTPS_PORT}"
        else
            echo "  ❌ 未运行"
        fi
        echo ""
        echo "本地服务访问地址:"
        echo "  - 登录门户: http://${SSO_WEB_HOST}:${APISIX_HTTP_PORT}"
        echo "  - 管理平台: http://${ADMIN_WEB_HOST}:${APISIX_HTTP_PORT}"
        echo "  - API 网关: http://${API_HOST}:${APISIX_HTTP_PORT}"
        echo ""
        echo "注意: 其他服务请使用本地启动脚本:"
        echo "  bash ops/scripts/local/start.sh"
    else
        docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
        echo ""
        echo "访问地址:"
        echo "  - 登录门户: http://${SSO_WEB_HOST}"
        echo "  - 管理平台: http://${ADMIN_WEB_HOST}"
        echo "  - API 网关: http://${API_HOST}"
    fi
    
    echo ""
    echo "Hosts 配置:"
    echo "  127.0.0.1 ${SSO_WEB_HOST} ${ADMIN_WEB_HOST} ${API_HOST}"
    echo ""
}

echo "========================================"
echo "  Docker 服务管理脚本"
echo "========================================"
echo "环境类型: $ENV_TYPE"
echo "环境配置: $ENV_CONFIG"
echo "操作: $ACTION"
echo "环境文件: $ENV_FILE"
echo "Compose 文件: $COMPOSE_FILE"
echo "服务: ${SERVICES:-全部}"
echo ""

case "$ACTION" in
    up)
        load_env "$ENV_FILE"
        echo "启动服务..."
        docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up $DETACH $SERVICES
        echo ""
        show_status
        ;;
    
    down)
        load_env "$ENV_FILE"
        echo "停止服务..."
        docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" down $SERVICES
        echo ""
        echo "服务已停止"
        ;;
    
    restart)
        load_env "$ENV_FILE"
        echo "重启服务..."
        docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" down $SERVICES
        sleep 2
        docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up $DETACH $SERVICES
        echo ""
        show_status
        ;;
    
    logs)
        SERVICE_NAME="$4"
        load_env "$ENV_FILE"
        if [ -n "$SERVICE_NAME" ]; then
            docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs -f "$SERVICE_NAME"
        else
            docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs -f
        fi
        ;;
    
    ps|status)
        show_status
        ;;
    
    build)
        echo "构建所有镜像..."
        bash "$SCRIPT_DIR/build-all.sh"
        ;;
    
    *)
        usage
        ;;
esac
