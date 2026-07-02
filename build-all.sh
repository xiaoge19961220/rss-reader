#!/bin/bash
set -e

APP_NAME="rss-reader"
TARGET="target"

cd "$(dirname "$0")"

# 从 AppConfig.java 动态读取版本号 (唯一版本来源)
VERSION=$(grep 'VERSION = "' src/main/java/com/rssreader/AppConfig.java | sed 's/.*"\(.*\)".*/\1/')
JAR_NAME="${APP_NAME}-${VERSION}.jar"
PORTABLE_DIR="${TARGET}/portable/${APP_NAME}-portable"
RUNTIME_DIR="${PORTABLE_DIR}/runtime"

echo "========================================="
echo "  RSS 阅读器 — 双包构建"
echo "  版本: ${VERSION}"
echo "========================================="

# ============ Step 1: 标准 JAR ============
echo ""
echo "[1/4] 编译 + 打包标准 JAR ..."
mvn clean package -DskipTests -q

# Maven 用 pom.xml 的版本号命名 JAR，重命名为 AppConfig 的版本
POM_JAR=$(ls ${TARGET}/${APP_NAME}-*.jar 2>/dev/null | head -1)
if [ "$POM_JAR" != "${TARGET}/${JAR_NAME}" ]; then
    mv "$POM_JAR" "${TARGET}/${JAR_NAME}"
fi
echo "  ✓ ${TARGET}/${JAR_NAME}"

# ============ Step 2: jlink 创建最小 JRE ============
echo ""
echo "[2/4] jlink 创建内嵌 JRE ..."
rm -rf "${RUNTIME_DIR}"

JLINK="${JAVA_HOME}/bin/jlink"
if [ ! -x "$JLINK" ]; then
    JLINK=$(which jlink 2>/dev/null || echo "")
fi
if [ -z "$JLINK" ] || [ ! -x "$JLINK" ]; then
    echo "  ⚠️ jlink 不可用，跳过 portable 包"
    exit 0
fi

"$JLINK" \
    --add-modules java.base,java.desktop,java.xml \
    --output "${RUNTIME_DIR}" \
    --strip-debug \
    --compress=2 \
    --no-header-files \
    --no-man-pages
echo "  ✓ 内嵌 JRE: $(du -sh "${RUNTIME_DIR}" | cut -f1)"

# ============ Step 3: 组装 portable ============
echo ""
echo "[3/4] 组装 portable 目录 ..."
mkdir -p "${PORTABLE_DIR}"
cp "${TARGET}/${JAR_NAME}" "${PORTABLE_DIR}/"

cat > "${PORTABLE_DIR}/run.sh" << SCRIPT
#!/bin/bash
DIR="\$(cd "\$(dirname "\$0")" && pwd)"
exec "\$DIR/runtime/bin/java" -Xms32m -Xmx256m -jar "\$DIR/${JAR_NAME}"
SCRIPT
chmod +x "${PORTABLE_DIR}/run.sh"

cat > "${PORTABLE_DIR}/run.bat" << BAT
@echo off
start "" "%~dp0runtime\bin\javaw.exe" -Xms32m -Xmx256m -jar "%~dp0${JAR_NAME}"
BAT
echo "  ✓ 启动脚本已生成"

# ============ Step 4: 压缩 ============
echo ""
echo "[4/4] 压缩 portable 包 ..."
rm -f "${TARGET}/${APP_NAME}-portable-${VERSION}.zip"
(cd "${TARGET}/portable" && zip -qr "../${APP_NAME}-portable-${VERSION}.zip" "${APP_NAME}-portable")
echo "  ✓ ${TARGET}/${APP_NAME}-portable-${VERSION}.zip ($(du -sh "${TARGET}/${APP_NAME}-portable-${VERSION}.zip" | cut -f1))"

echo ""
echo "========================================="
echo "  构建完成！"
echo "========================================="
echo "  📦 标准 JAR: ${TARGET}/${JAR_NAME}"
echo "  📦 Portable: ${TARGET}/${APP_NAME}-portable-${VERSION}.zip"
