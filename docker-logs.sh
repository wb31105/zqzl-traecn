#!/bin/bash

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

SERVICE=$1

if [ -z "$SERVICE" ]; then
    echo "========================================"
    echo "  ZQZL 微服务群 - 查看所有服务日志"
    echo "========================================"
    echo ""
    echo "查看单个服务日志用法: ./docker-logs.sh [服务名]"
    echo "可用服务名: eureka-server, gateway-server, user-server, sso-server, sso-web, user-web, nginx-gateway"
    echo ""
    echo "查看所有服务日志..."
    echo "----------------------------------------"

    if command -v docker-compose &> /dev/null; then
        docker-compose logs -f
    else
        docker compose logs -f
    fi
else
    echo "========================================"
    echo "  查看 $SERVICE 服务日志"
    echo "========================================"

    if command -v docker-compose &> /dev/null; then
        docker-compose logs -f "$SERVICE"
    else
        docker compose logs -f "$SERVICE"
    fi
fi
