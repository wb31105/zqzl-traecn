#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
NGINX_CONF="$SCRIPT_DIR/nginx.conf"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"

echo "========================================"
echo "  启动 Nginx 前端网关"
echo "========================================"
echo "配置文件: $NGINX_CONF"
echo ""

# 先停止已有的容器
if [ "$(docker ps -q -f name=zqzl-nginx-gateway)" ]; then
    echo "停止已存在的 zqzl-nginx-gateway 容器..."
    docker stop zqzl-nginx-gateway > /dev/null 2>&1
    docker rm zqzl-nginx-gateway > /dev/null 2>&1
fi

if [ "$(docker ps -aq -f name=zqzl-nginx-gateway)" ]; then
    docker rm zqzl-nginx-gateway > /dev/null 2>&1
fi

echo "使用 Docker 启动 Nginx 网关..."
docker run -d --name zqzl-nginx-gateway \
    -p 80:80 \
    -v "$NGINX_CONF:/etc/nginx/nginx.conf:ro" \
    --add-host=host.docker.internal:host-gateway \
    nginx:alpine

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Nginx 网关启动成功！"
    echo "🌐 访问地址: http://localhost"
    echo ""
    echo "查看日志命令:"
    echo "  docker logs -f zqzl-nginx-gateway"
    echo ""
    echo "停止命令:"
    echo "  docker stop zqzl-nginx-gateway"
else
    echo ""
    echo "❌ Nginx 启动失败"
    exit 1
fi
