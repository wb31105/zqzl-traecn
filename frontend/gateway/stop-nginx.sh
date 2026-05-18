#!/bin/bash

echo "========================================"
echo "  停止 Nginx 前端网关"
echo "========================================"

if [ "$(docker ps -q -f name=zqzl-nginx-gateway)" ]; then
    echo "正在停止 Docker Nginx 容器..."
    docker stop zqzl-nginx-gateway
    docker rm zqzl-nginx-gateway
    echo "✅ Nginx 容器已停止并移除"
else
    echo "Nginx 容器未运行"
fi

# 如果有本地 Nginx 进程也尝试停止
if pgrep -x "nginx" > /dev/null; then
    echo "检测到本地 Nginx 进程，正在停止..."
    nginx -s stop 2>/dev/null || pkill -x nginx
    sleep 2
    if pgrep -x "nginx" > /dev/null; then
        echo "强制停止 Nginx..."
        pkill -9 -x nginx
    fi
fi
