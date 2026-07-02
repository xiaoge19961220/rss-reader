#!/bin/bash
set -e

APP_NAME="rss-reader"
VERSION="1.0.0"
JAR_NAME="${APP_NAME}-${VERSION}.jar"
TARGET="target"
PORTABLE_DIR="${TARGET}/portable/${APP_NAME}-portable"
RUNTIME_DIR="${PORTABLE_DIR}/runtime"

cd "$(dirname "$0")"

echo "========================================="
echo "  RSS 阅读器 — 双包构建"
echo "========================================="

# ============ Step 1: 标准 JAR ============
echo ""
echo "[1/4] 编译 + 打包标准 JAR ..."
mvn clean package -DskipTests -q
echo "  ✓ ${TARGET}/${JAR_NAME}"

# ============ Step 2: jlink 创建最小 JRE ============
echo ""
echo "[2/4] jlink 创建内嵌 JRE ..."
rm -rf "${RUNTIME_DIR}"

# CI 环境用 JAVA_HOME，本地环境尝试 which
JLINK="${JAVA_HOME}/bin/jlink"
if [ ! -x "$JLINK" ]; then
    JLINK=$(which jlink 2>/dev/null || echo "")
fi
if [ -z "$JLINK" ] || [ ! -x "$JLINK" ]; then
    echo "  ⚠️ jlink 不可用，跳过 portable 包构建"
    echo "  (仅在本地完整 JDK 环境才构建内嵌 JRE 版本)"
    exit 0
fi

"$JLINK" \
    --add-modules java.base,java.desktop,java.xml \
    --output "${RUNTIME_DIR}" \
    --strip-debug \
    --compress=2 \
    --no-header-files \
    --no-man-pages
JRE_SIZE=$(du -sh "${RUNTIME_DIR}" | cut -f1)
echo "  ✓ 内嵌 JRE: ${JRE_SIZE}"

# ============ Step 3: 组装 portable ============
echo ""
echo "[3/4] 组装 portable 目录 ..."
cp "${TARGET}/${JAR_NAME}" "${PORTABLE_DIR}/"

cat > "${PORTABLE_DIR}/run.sh" << 'SCRIPT'
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$DIR/runtime/bin/java" -Xms32m -Xmx256m -jar "$DIR/rss-reader-1.0.0.jar"
SCRIPT
chmod +x "${PORTABLE_DIR}/run.sh"

cat > "${PORTABLE_DIR}/run.bat" << 'BAT'
@echo off
start "" "%~dp0runtime\bin\javaw.exe" -Xms32m -Xmx256m -jar "%~dp0rss-reader-1.0.0.jar"
BAT
echo "  ✓ 启动脚本已生成"

# ============ Step 4: 压缩 ============
echo ""
echo "[4/4] 压缩 portable 包 ..."
rm -f "${TARGET}/${APP_NAME}-portable-${VERSION}.zip"
(cd "${TARGET}/portable" && zip -qr "../${APP_NAME}-portable-${VERSION}.zip" "${APP_NAME}-portable")
PORTABLE_ZIP_SIZE=$(du -sh "${TARGET}/${APP_NAME}-portable-${VERSION}.zip" | cut -f1)
echo "  ✓ ${TARGET}/${APP_NAME}-portable-${VERSION}.zip"

echo ""
echo "========================================="
echo "  构建完成！"
echo "========================================="
echo "  📦 标准 JAR: ${TARGET}/${JAR_NAME}"
echo "  📦 Portable: ${TARGET}/${APP_NAME}-portable-${VERSION}.zip (${PORTABLE_ZIP_SIZE})"
