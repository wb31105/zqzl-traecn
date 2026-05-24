#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

cd "$PROJECT_ROOT/ops/docker/apisix"

echo "========================================"
echo "  构建 APISIX 网关镜像"
echo "========================================"

docker build -t zqzl/apisix-gateway:latest .

echo ""
echo "APISIX 网关镜像构建完成！"
echo "镜像名称: zqzl/apisix-gateway:latest"
echo ""
echo "镜像特点:"
echo "  - 纯净镜像，不含配置文件"
echo "  - 配置文件通过挂载方式注入"
echo ""
echo "使用方式:"
echo "  - 集成环境: docker compose up -d (自动挂载配置)"
echo "  - 本地环境: docker compose -f docker-compose-local.yml up -d"
echo ""
echo "配置文件位置:"
echo "  - 集成环境: ./ops/docker/apisix/config.yaml + apisix.yaml"
echo "  - 本地环境: ./ops/docker/apisix/config-local.yaml + apisix-local.yaml"
echo "========================================"
