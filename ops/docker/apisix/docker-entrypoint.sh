#!/bin/sh
set -e

CONFIG_DIR="/usr/local/apisix/conf"
TEMPLATE_FILE="$CONFIG_DIR/apisix.yaml.template"
OUTPUT_FILE="$CONFIG_DIR/apisix.yaml"

echo "========================================"
echo "  APISIX 配置初始化"
echo "========================================"

echo "环境变量:"
echo "  API_HOST: ${API_HOST}"
echo "  SSO_WEB_HOST: ${SSO_WEB_HOST}"
echo "  ADMIN_WEB_HOST: ${ADMIN_WEB_HOST}"
echo "  SSO_SERVER_UPSTREAM: ${SSO_SERVER_UPSTREAM}"
echo "  USER_SERVER_UPSTREAM: ${USER_SERVER_UPSTREAM}"
echo "  SSO_WEB_UPSTREAM: ${SSO_WEB_UPSTREAM}"
echo "  USER_WEB_UPSTREAM: ${USER_WEB_UPSTREAM}"

if [ -f "$TEMPLATE_FILE" ]; then
    echo ""
    echo "正在替换配置模板中的环境变量..."
    
    cp "$TEMPLATE_FILE" "$OUTPUT_FILE"
    
    sed -i "s|\${API_HOST}|${API_HOST}|g" "$OUTPUT_FILE"
    sed -i "s|\${SSO_WEB_HOST}|${SSO_WEB_HOST}|g" "$OUTPUT_FILE"
    sed -i "s|\${ADMIN_WEB_HOST}|${ADMIN_WEB_HOST}|g" "$OUTPUT_FILE"
    sed -i "s|\${SSO_SERVER_UPSTREAM}|${SSO_SERVER_UPSTREAM}|g" "$OUTPUT_FILE"
    sed -i "s|\${USER_SERVER_UPSTREAM}|${USER_SERVER_UPSTREAM}|g" "$OUTPUT_FILE"
    sed -i "s|\${SSO_WEB_UPSTREAM}|${SSO_WEB_UPSTREAM}|g" "$OUTPUT_FILE"
    sed -i "s|\${USER_WEB_UPSTREAM}|${USER_WEB_UPSTREAM}|g" "$OUTPUT_FILE"
    
    echo "配置生成完成: $OUTPUT_FILE"
    echo ""
    echo "生成的路由配置:"
    grep -E "(host:|nodes:)" "$OUTPUT_FILE" | head -20
else
    echo "警告: 模板文件不存在 $TEMPLATE_FILE"
fi

echo "========================================"
echo "  启动 APISIX"
echo "========================================"
exec "$@"
