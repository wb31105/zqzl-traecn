#!/bin/bash

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

echo "========================================"
echo "  ZQZL 微服务群 - Docker 镜像构建"
echo "========================================"
echo ""

echo "[1/7] 构建 Eureka Server 镜像..."
echo "----------------------------------------"
docker build -f .deploy/docker/backend/eureka-server.Dockerfile -t zqzl/eureka-server:latest .

echo ""
echo "[2/7] 构建 Gateway Server 镜像..."
echo "----------------------------------------"
docker build -f .deploy/docker/backend/gateway-server.Dockerfile -t zqzl/gateway-server:latest .

echo ""
echo "[3/7] 构建 User Server 镜像..."
echo "----------------------------------------"
docker build -f .deploy/docker/backend/user-server.Dockerfile -t zqzl/user-server:latest .

echo ""
echo "[4/7] 构建 SSO Server 镜像..."
echo "----------------------------------------"
docker build -f .deploy/docker/backend/sso-server.Dockerfile -t zqzl/sso-server:latest .

echo ""
echo "[5/7] 构建 SSO Web 镜像..."
echo "----------------------------------------"
docker build -f .deploy/docker/frontend/sso-web.Dockerfile -t zqzl/sso-web:latest .

echo ""
echo "[6/7] 构建 User Web 镜像..."
echo "----------------------------------------"
docker build -f .deploy/docker/frontend/user-web.Dockerfile -t zqzl/user-web:latest .

echo ""
echo "[7/7] 构建 Nginx Gateway 镜像..."
echo "----------------------------------------"
docker build -f .deploy/docker/nginx/nginx.Dockerfile -t zqzl/nginx-gateway:latest .

echo ""
echo "========================================"
echo "  所有镜像构建完成！"
echo "========================================"
echo "可用镜像列表："
echo "  - zqzl/eureka-server:latest"
echo "  - zqzl/gateway-server:latest"
echo "  - zqzl/user-server:latest"
echo "  - zqzl/sso-server:latest"
echo "  - zqzl/sso-web:latest"
echo "  - zqzl/user-web:latest"
echo "  - zqzl/nginx-gateway:latest"
echo ""
echo "使用以下命令启动服务："
echo "  ./docker-start-all.sh"
echo "========================================"
