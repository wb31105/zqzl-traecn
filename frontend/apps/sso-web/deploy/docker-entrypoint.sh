#!/bin/sh
set -e

echo "========================================"
echo "  SSO Web 环境变量初始化"
echo "========================================"

TEMPLATE_FILE="/usr/share/nginx/html/env-config.js.template"
OUTPUT_FILE="/usr/share/nginx/html/env-config.js"

echo "环境变量:"
echo "  REACT_APP_SSO_SERVER_URL: ${REACT_APP_SSO_SERVER_URL}"
echo "  REACT_APP_API_SERVER_URL: ${REACT_APP_API_SERVER_URL}"
echo "  REACT_APP_USER_WEB_URL: ${REACT_APP_USER_WEB_URL}"

if [ -f "$TEMPLATE_FILE" ]; then
    echo ""
    echo "正在替换配置模板中的环境变量..."
    
    cp "$TEMPLATE_FILE" "$OUTPUT_FILE"
    
    sed -i "s|\${REACT_APP_SSO_SERVER_URL}|${REACT_APP_SSO_SERVER_URL}|g" "$OUTPUT_FILE"
    sed -i "s|\${REACT_APP_API_SERVER_URL}|${REACT_APP_API_SERVER_URL}|g" "$OUTPUT_FILE"
    sed -i "s|\${REACT_APP_USER_WEB_URL}|${REACT_APP_USER_WEB_URL}|g" "$OUTPUT_FILE"
    
    echo "配置生成完成: $OUTPUT_FILE"
    echo ""
    cat "$OUTPUT_FILE"
else
    echo "警告: 模板文件不存在 $TEMPLATE_FILE"
fi

echo "========================================"
echo "  启动 Nginx"
echo "========================================"
exec "$@"
