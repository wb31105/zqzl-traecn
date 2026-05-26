#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
cd "$PROJECT_ROOT"

usage() {
    echo "用法: $0 <服务名称> [镜像标签] [镜像前缀]"
    echo ""
    echo "服务名称:"
    echo "  all          - 构建所有服务"
    echo "  apisix       - 构建 APISIX 网关"
    echo "  sso-server   - 构建 SSO 后端服务"
    echo "  user-server  - 构建 User 后端服务"
    echo "  backend      - 构建所有后端服务"
    echo "  sso-web      - 构建 SSO 前端应用"
    echo "  user-web     - 构建 User 前端应用"
    echo "  frontend     - 构建所有前端应用"
    echo ""
    echo "示例:"
    echo "  $0 apisix"
    echo "  $0 sso-server v1.0.0"
    echo "  $0 all latest mycompany"
    exit 1
}

SERVICE="${1:-all}"
IMAGE_TAG="${2:-latest}"
IMAGE_PREFIX="${3:-zqzl}"

get_service_config() {
    local service_name="$1"
    case "$service_name" in
        apisix)
            echo "ops/docker/apisix/Dockerfile|apisix-gateway"
            ;;
        sso-server)
            echo "backend/services/sso-server/deploy/Dockerfile|sso-server"
            ;;
        user-server)
            echo "backend/services/user-server/deploy/Dockerfile|user-server"
            ;;
        sso-web)
            echo "frontend/apps/sso-web/deploy/Dockerfile|sso-web"
            ;;
        user-web)
            echo "frontend/apps/user-web/deploy/Dockerfile|user-web"
            ;;
        *)
            echo ""
            ;;
    esac
}

build_service() {
    local service_name="$1"
    local config=$(get_service_config "$service_name")

    if [ -z "$config" ]; then
        echo "错误: 未知服务 '$service_name'"
        exit 1
    fi

    local dockerfile=$(echo "$config" | cut -d'|' -f1)
    local app_name=$(echo "$config" | cut -d'|' -f2)
    local image_name="${IMAGE_PREFIX}/${app_name}"

    echo "========================================"
    echo "  构建 ${service_name} 镜像"
    echo "========================================"
    echo "Dockerfile: $dockerfile"
    echo "镜像名称: ${image_name}:${IMAGE_TAG}"
    echo ""

    docker build \
        -f "$dockerfile" \
        -t "${image_name}:${IMAGE_TAG}" \
        .

    echo ""
    echo "✓ ${service_name} 镜像构建完成: ${image_name}:${IMAGE_TAG}"
    echo ""
}

echo "========================================"
echo "  Docker 镜像构建工具"
echo "========================================"
echo "镜像标签: ${IMAGE_PREFIX}/<service>:${IMAGE_TAG}"
echo ""

case "$SERVICE" in
    all)
        build_service "apisix"
        build_service "user-server"
        build_service "sso-server"
        build_service "sso-web"
        build_service "user-web"
        ;;
    backend)
        build_service "user-server"
        build_service "sso-server"
        ;;
    frontend)
        build_service "sso-web"
        build_service "user-web"
        ;;
    apisix|sso-server|user-server|sso-web|user-web)
        build_service "$SERVICE"
        ;;
    *)
        usage
        ;;
esac

echo "========================================"
echo "  构建完成！"
echo "========================================"
echo ""
echo "使用以下命令启动服务（集成环境）:"
echo "  bash ops/scripts/docker/start.sh integration default up"
echo ""
echo "使用以下命令启动服务（本地环境）:"
echo "  bash ops/scripts/local/start.sh"
echo ""
