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

    # 1. 检查是否有影响编译的源文件改动（排除脚本文件本身）
    if [ -d "$PROJECT_DIR/.git" ]; then
        # 获取改动的文件列表，只检查会影响APK编译的文件类型
        changed_source_files=$(git diff --name-only HEAD 2>/dev/null | grep -E '\.(kt|xml|gradle|kts|properties|md)$' | head -5)
        if [ -n "$changed_source_files" ]; then
            echo -e "${YELLOW}检测到源代码改动${NC}"
            echo -e "${YELLOW}改动文件:${NC}"
            echo "$changed_source_files" | sed 's/^/  - /'
            NEED_COMPILE=true
        fi

        # 检查是否有新文件未跟踪
        if [ -n "$(git ls-files --others --exclude-standard 2>/dev/null)" ]; then
            echo -e "${YELLOW}检测到未跟踪的新文件${NC}"
            NEED_COMPILE=true
        fi

        # 2. 检查自上次编译以来是否有相关文件改动
        if [ "$NEED_COMPILE" = false ]; then
            # 获取 APK 最后修改时间
            APK_MTIME=$(stat -c %Y "$APK_PATH" 2>/dev/null || stat -f %m "$APK_PATH" 2>/dev/null)

            # 使用 git ls-files 获取被跟踪的源文件（自动排除 .gitignore 中的文件）
            for ext in "${CHECK_EXTENSIONS[@]}"; do
                # 查找比 APK 新的已跟踪文件
                newer_files=$(git ls-files -z "$PROJECT_DIR" 2>/dev/null | \
                    xargs -0 -I {} sh -c 'if [ -f "{}" ] && [ "{}" -nt "$1" ]; then echo "{}"; fi' _ "$APK_PATH" 2>/dev/null | \
                    grep -E "\\.${ext#\\*\\.}$" | head -5)

                if [ -n "$newer_files" ]; then
                    echo -e "${YELLOW}检测到源文件比 APK 新，需要重新编译${NC}"
                    echo -e "${YELLOW}改动文件示例:${NC}"
                    echo "$newer_files" | head -3 | sed 's/^/  - /'
                    NEED_COMPILE=true
                    break
                fi
            done
        fi
    else
        # 没有 git，检查文件时间戳
        echo -e "${YELLOW}未找到 Git，使用文件时间戳检查${NC}"

        APK_MTIME=$(stat -c %Y "$APK_PATH" 2>/dev/null || stat -f %m "$APK_PATH" 2>/dev/null)

        for ext in "${CHECK_EXTENSIONS[@]}"; do
            newer_files=$(find "$PROJECT_DIR" -name "$ext" -newer "$APK_PATH" -not -path "*/build/*" -not -path "*/.gradle/*" 2>/dev/null | head -1)

            if [ -n "$newer_files" ]; then
                NEED_COMPILE=true
                break
            fi
        done
    fi

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

# 获取所有设备列表（处理设备名中包含空格的情况）
# 删除行尾的空格和 "device" 字样，保留前面的设备ID
DEVICES=$(adb devices | grep "device$" | sed 's/[[:space:]]*device$//')
DEVICE_COUNT=$(echo "$DEVICES" | grep -c '.' | tr -d ' ')

echo -e "${BLUE}检测到设备:${NC}"
echo "$DEVICES" | sed 's/^/  - /'
echo ""

# 安装成功计数
INSTALL_SUCCESS=0
INSTALL_FAILED=0

# 执行安装到所有设备
echo -e "${BLUE}安装 APK 到 ${DEVICE_COUNT} 个设备${NC}"
echo ""

# 创建临时文件用于在子 shell 中传递计数
SUCCESS_FILE=$(mktemp)
FAILED_FILE=$(mktemp)
echo 0 > "$SUCCESS_FILE"
echo 0 > "$FAILED_FILE"

# 使用 while read 处理带空格的设备名
echo "$DEVICES" | while IFS= read -r DEVICE; do
    [ -z "$DEVICE" ] && continue  # 跳过空行

    echo -e "${BLUE}[${DEVICE}] 开始安装...${NC}"

    if adb -s "$DEVICE" install -r "$APK_PATH" 2>/dev/null; then
        echo -e "${GREEN}[${DEVICE}] 安装成功${NC}"
        # 更新临时文件中的计数
        CURRENT=$(cat "$SUCCESS_FILE")
        echo $((CURRENT + 1)) > "$SUCCESS_FILE"

        # 启动应用
        adb -s "$DEVICE" shell am start -n "com.example.weiqigame/.MainActivity" 2>/dev/null
        echo -e "${BLUE}[${DEVICE}] 已启动应用${NC}"
    else
        echo -e "${RED}[${DEVICE}] 安装失败${NC}"
        # 更新临时文件中的计数
        CURRENT=$(cat "$FAILED_FILE")
        echo $((CURRENT + 1)) > "$FAILED_FILE"
    fi
    echo ""
done

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
