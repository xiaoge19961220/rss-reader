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

# ============ Step 1: 标准 JAR (需要 Java 环境) ============
echo ""
echo "[1/4] 编译 + 打包标准 JAR ..."
mvn clean package -DskipTests -q
echo "  ✓ ${TARGET}/${JAR_NAME}"

# ============ Step 2: jlink 创建最小 JRE ============
echo ""
echo "[2/4] jlink 创建内嵌 JRE (java.base + java.desktop + java.xml) ..."
rm -rf "${RUNTIME_DIR}"
jlink \
    --add-modules java.base,java.desktop,java.xml \
    --output "${RUNTIME_DIR}" \
    --strip-debug \
    --compress=2 \
    --no-header-files \
    --no-man-pages
JRE_SIZE=$(du -sh "${RUNTIME_DIR}" | cut -f1)
echo "  ✓ 内嵌 JRE: ${JRE_SIZE}"

# ============ Step 3: 复制 JAR + 创建启动脚本 ============
echo ""
echo "[3/4] 组装 portable 目录 ..."
cp "${TARGET}/${JAR_NAME}" "${PORTABLE_DIR}/"

# macOS / Linux 启动脚本
cat > "${PORTABLE_DIR}/run.sh" << 'SCRIPT'
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$DIR/runtime/bin/java" -Xms32m -Xmx256m -jar "$DIR/rss-reader-1.0.0.jar"
SCRIPT
chmod +x "${PORTABLE_DIR}/run.sh"

# Windows 启动脚本
cat > "${PORTABLE_DIR}/run.bat" << 'BAT'
@echo off
start "" "%~dp0runtime\bin\javaw.exe" -Xms32m -Xmx256m -jar "%~dp0rss-reader-1.0.0.jar"
BAT

echo "  ✓ 启动脚本已生成"

# ============ Step 4: 打包 portable 为 zip ============
echo ""
echo "[4/4] 压缩 portable 包 ..."
rm -f "${TARGET}/${APP_NAME}-portable-${VERSION}.zip"
(cd "${TARGET}/portable" && zip -qr "../${APP_NAME}-portable-${VERSION}.zip" "${APP_NAME}-portable")
PORTABLE_ZIP_SIZE=$(du -sh "${TARGET}/${APP_NAME}-portable-${VERSION}.zip" | cut -f1)
echo "  ✓ ${TARGET}/${APP_NAME}-portable-${VERSION}.zip"

# ============ 输出 ============
echo ""
echo "========================================="
echo "  构建完成！"
echo "========================================="
echo ""
echo "  📦 有 Java 环境 (纯 JAR):"
echo "     ${TARGET}/${JAR_NAME}"
JAR_SIZE=$(du -sh "${TARGET}/${JAR_NAME}" | cut -f1)
echo "     大小: ${JAR_SIZE}"
echo "     运行: java -jar ${JAR_NAME}"
echo ""
echo "  📦 无 Java 环境 (内嵌 JRE):"
echo "     目录: ${PORTABLE_DIR}"
echo "     ZIP:  ${TARGET}/${APP_NAME}-portable-${VERSION}.zip"
echo "     大小: ${PORTABLE_ZIP_SIZE}"
echo "     macOS/Linux: 双击 run.sh"
echo "     Windows:     双击 run.bat"
echo ""
echo "  内嵌 JRE 大小: ${JRE_SIZE}"
