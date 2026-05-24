#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

cd "$PROJECT_ROOT/ops/docker/apisix"

echo "========================================"
echo "  构建 APISIX 网关镜像（Docker 环境）"
echo "========================================"

docker build \
  --build-arg APISIX_CONFIG=apisix.yaml \
  --build-arg APISIX_CONFIG_FILE=config.yaml \
  -t zqzl/apisix-gateway:latest .

echo ""
echo "APISIX 网关镜像构建完成！"
echo "镜像名称: zqzl/apisix-gateway:latest"
echo "适用环境: Docker Compose"
echo "配置说明: 使用 Docker 服务名和域名"
echo "  - Upstream: sso-server:8080, user-server:8080, sso-web:80..."
echo "  - 域名: api.bw.com, sso.bw.com, admin.bw.com"
echo "========================================"
