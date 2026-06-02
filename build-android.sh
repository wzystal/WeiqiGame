#!/bin/bash

# 围棋应用编译安装脚本
# 功能：检查代码是否有改动，无改动则直接安装，有改动则编译后安装

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目路径
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_MODULE="app"
APK_PATH="$PROJECT_DIR/$APP_MODULE/build/outputs/apk/debug/${APP_MODULE}-debug.apk"

# Git 相关文件检查（用于判断是否需要重新编译）
# 包括：.kt, .xml, .gradle, .kts, .properties 文件
CHECK_EXTENSIONS=("*.kt" "*.xml" "*.gradle" "*.kts" "*.properties" "*.md")

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  围棋应用编译安装脚本${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 检查是否在项目根目录
if [ ! -f "$PROJECT_DIR/settings.gradle.kts" ] && [ ! -f "$PROJECT_DIR/settings.gradle" ]; then
    echo -e "${RED}错误：请在项目根目录运行此脚本${NC}"
    exit 1
fi

# 检查是否存在已编译的 APK
if [ ! -f "$APK_PATH" ]; then
    echo -e "${YELLOW}未找到现有 APK，需要编译${NC}"
    NEED_COMPILE=true
else
    # 检查代码是否有改动
    echo -e "${BLUE}检查代码改动...${NC}"

    NEED_COMPILE=false

    # 使用文件时间戳检测改动（更准确，不受 git 状态影响）
    echo -e "${BLUE}使用文件时间戳检查...${NC}"
    APK_MTIME=$(stat -f %m "$APK_PATH" 2>/dev/null || stat -c %Y "$APK_PATH" 2>/dev/null)

    for ext in "${CHECK_EXTENSIONS[@]}"; do
        newer_files=$(find "$PROJECT_DIR" -name "$ext" -newer "$APK_PATH" -not -path "*/build/*" -not -path "*/.gradle/*" 2>/dev/null | head -5)
        if [ -n "$newer_files" ]; then
            echo -e "${YELLOW}检测到源代码改动${NC}"
            echo -e "${YELLOW}改动文件:${NC}"
            echo "$newer_files" | sed 's/^/  - /'
            NEED_COMPILE=true
            break
        fi
    done

    if [ "$NEED_COMPILE" = false ]; then
        echo -e "${GREEN}代码无改动，跳过编译${NC}"
    fi
fi

# 执行编译（如果需要）
if [ "$NEED_COMPILE" = true ]; then
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  开始编译项目${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""

    # 清理旧构建（可选，如需增量编译可注释掉）
    # echo -e "${YELLOW}清理旧构建...${NC}"
    # ./gradlew clean

    # 编译 Debug APK
    echo -e "${BLUE}执行编译: ./gradlew :${APP_MODULE}:assembleDebug${NC}"
    ./gradlew :"${APP_MODULE}":assembleDebug

    if [ ! -f "$APK_PATH" ]; then
        echo -e "${RED}编译失败：未生成 APK 文件${NC}"
        exit 1
    fi

    echo ""
    echo -e "${GREEN}编译成功！${NC}"
else
    echo -e "${GREEN}使用已有 APK: $APK_PATH${NC}"
fi

# 安装 APK
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  开始安装 APK${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 检查设备连接
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}错误：未检测到 Android 设备连接${NC}"
    echo -e "${YELLOW}请确保：${NC}"
    echo -e "  1. 手机已连接 USB 并开启调试模式"
    echo -e "  2. 已授权调试权限"
    echo -e "  3. adb 命令可用"
    exit 1
fi

# 安装成功计数
INSTALL_SUCCESS=0
INSTALL_FAILED=0

# 创建临时文件用于在子 shell 中传递计数
SUCCESS_FILE=$(mktemp)
FAILED_FILE=$(mktemp)
echo 0 > "$SUCCESS_FILE"
echo 0 > "$FAILED_FILE"

# 创建设备列表临时文件
DEVICE_LIST_FILE=$(mktemp)
adb devices | grep "device$" | sed 's/[[:space:]]*device$//' > "$DEVICE_LIST_FILE"

# 获取设备数量
DEVICE_COUNT=$(wc -l < "$DEVICE_LIST_FILE" | tr -d ' ')

echo -e "${BLUE}检测到 ${DEVICE_COUNT} 个设备:${NC}"
cat "$DEVICE_LIST_FILE" | sed 's/^/  - /'
echo ""

# 调试：显示设备列表文件的行数和每行内容
echo -e "${YELLOW}调试：设备列表文件详细信息${NC}"
echo "文件行数: $(wc -l < "$DEVICE_LIST_FILE" | tr -d ' ')"
echo "文件大小: $(wc -c < "$DEVICE_LIST_FILE" | tr -d ' ') 字节"
echo ""

# 执行安装到所有设备
echo -e "${BLUE}安装 APK 到 ${DEVICE_COUNT} 个设备${NC}"
echo ""

# 逐行处理设备列表
INDEX=0
while IFS= read -r DEVICE || [ -n "$DEVICE" ]; do
    INDEX=$((INDEX + 1))
    echo -e "${YELLOW}调试：读取第 ${INDEX} 行设备ID: [${DEVICE}]${NC}"
    [ -z "$DEVICE" ] && continue  # 跳过空行

    echo -e "${BLUE}[${DEVICE}] 开始安装...${NC}"

    # 直接尝试安装，不需要额外检查设备状态（adb devices 已经过滤过了）
    # 将 stdin 重定向到 /dev/null，避免 adb 命令消耗 while 循环的输入
    if adb -s "$DEVICE" install -r "$APK_PATH" </dev/null 2>&1; then
        echo -e "${GREEN}[${DEVICE}] 安装成功${NC}"
        # 更新临时文件中的计数
        CURRENT=$(cat "$SUCCESS_FILE")
        echo $((CURRENT + 1)) > "$SUCCESS_FILE"

        # 启动应用
        adb -s "$DEVICE" shell am start -n "com.example.weiqigame/.MainActivity" </dev/null 2>/dev/null
        echo -e "${BLUE}[${DEVICE}] 已启动应用${NC}"
    else
        echo -e "${RED}[${DEVICE}] 安装失败${NC}"
        # 更新临时文件中的计数
        CURRENT=$(cat "$FAILED_FILE")
        echo $((CURRENT + 1)) > "$FAILED_FILE"
    fi
    echo ""
done < "$DEVICE_LIST_FILE"

echo -e "${YELLOW}调试：循环结束，共处理 ${INDEX} 行${NC}"
echo ""

# 删除临时文件
rm -f "$DEVICE_LIST_FILE"

# 从临时文件读取最终计数
INSTALL_SUCCESS=$(cat "$SUCCESS_FILE")
INSTALL_FAILED=$(cat "$FAILED_FILE")
rm -f "$SUCCESS_FILE" "$FAILED_FILE"

# 汇总结果
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  安装完成${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "成功: ${INSTALL_SUCCESS} 个设备"
echo -e "失败: ${INSTALL_FAILED} 个设备"

if [ "$INSTALL_SUCCESS" -eq 0 ]; then
    echo ""
    echo -e "${RED}所有设备安装失败${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}完成！${NC}"
echo ""
