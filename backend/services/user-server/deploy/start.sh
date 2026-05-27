#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

MODE="${1:-docker}"

echo "========================================"
echo "  启动 User Server"
echo "========================================"
echo "模式: $MODE"
echo ""

if [ "$MODE" = "local" ]; then
    JAR_FILE="$PROJECT_DIR/target/user-server-1.0.0.jar"
    if [ ! -f "$JAR_FILE" ]; then
        echo "错误: JAR文件不存在: $JAR_FILE"
        echo "请先运行: mvn clean package -DskipTests"
        exit 1
    fi
    echo "本地模式"
    echo "JAR文件: $JAR_FILE"
    echo "Spring Profile: local"
    echo ""
    exec java -jar "$JAR_FILE" --spring.profiles.active=local
else
    JAR_FILE="/app/app.jar"
    ENV="${SPRING_PROFILES_ACTIVE:-prod}"
    echo "集成部署模式"
    echo "JAR文件: $JAR_FILE"
    echo "Spring Profile: $ENV"
    echo ""
    exec java -jar "$JAR_FILE" --spring.profiles.active="$ENV"
fi
