#!/bin/bash

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

echo "========================================"
echo "  ZQZL 微服务群 - Docker 一键启动"
echo "========================================"
echo ""

echo "检查 Docker Compose..."
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "错误: 未找到 docker-compose 或 Docker Compose V2"
    exit 1
fi

echo ""
echo "停止并清理旧容器..."
echo "----------------------------------------"
if command -v docker-compose &> /dev/null; then
    docker-compose down 2>/dev/null || true
else
    docker compose down 2>/dev/null || true
fi

echo ""
echo "启动所有服务..."
echo "----------------------------------------"
if command -v docker-compose &> /dev/null; then
    docker-compose up -d
else
    docker compose up -d
fi

echo ""
echo "========================================"
echo "  所有服务启动中！"
echo "========================================"
echo ""
echo "服务端口分配："
echo "  - Eureka 注册中心: http://localhost:8761"
echo "  - 后端 API 网关:   http://localhost:9000"
echo "  - User 后端服务:   http://localhost:8081"
echo "  - SSO 后端服务:    http://localhost:8080"
echo "  - SSO 前端:        http://localhost:3000"
echo "  - User 前端:       http://localhost:3031"
echo "  - Nginx 前端网关:  http://localhost"
echo ""
echo "========================================"
echo "  域名架构访问（推荐）"
echo "========================================"
echo "请先配置 hosts 文件："
echo "  127.0.0.1 sso.bw.com admin.bw.com api.bw.com"
echo ""
echo "统一域名访问："
echo "  - 登录门户:    http://sso.bw.com"
echo "  - 管理平台:    http://admin.bw.com"
echo "  - 后端 API:    http://api.bw.com/v1/**"
echo ""
echo "默认账号：admin / admin123"
echo ""
echo "查看服务日志: ./docker-logs.sh"
echo "停止所有服务: ./docker-stop-all.sh"
echo "========================================"
