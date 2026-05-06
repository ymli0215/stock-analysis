#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
STOCKSERVER_DIR="$SCRIPT_DIR/stockserver"
DEPLOY_DIR="/mnt/d/Environments/tomcat/10.1/webapps/StockServer/WEB-INF/classes"

echo "=== Stock Server Deploy ==="
echo "Source : $STOCKSERVER_DIR"
echo "Target : $DEPLOY_DIR"
echo ""

cd "$STOCKSERVER_DIR"

echo "[1/2] Compiling..."
mvn compile -q
if [ $? -ne 0 ]; then
    echo "❌ 編譯失敗，未部署"
    exit 1
fi
echo "✅ 編譯成功"

echo "[2/2] Deploying to Tomcat..."
cp -r target/classes/* "$DEPLOY_DIR/"
echo "✅ 部署完成 — $(date '+%Y-%m-%d %H:%M:%S')"
echo ""
echo "提醒：Tomcat 需要 reload 才能生效（或重啟 context）"
