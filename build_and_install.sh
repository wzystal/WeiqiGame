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

    # 1. 检查是否有未提交的改动
    if [ -d "$PROJECT_DIR/.git" ]; then
        # 检查工作区是否有未提交的修改
        if ! git diff --quiet HEAD -- 2>/dev/null; then
            echo -e "${YELLOW}检测到未提交的代码改动${NC}"
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

            # 检查源文件是否比 APK 新
            for ext in "${CHECK_EXTENSIONS[@]}"; do
                # 使用 find 查找比 APK 新的文件
                newer_files=$(find "$PROJECT_DIR" -name "$ext" -newer "$APK_PATH" -not -path "*/build/*" -not -path "*/.git/*" -not -path "*/.gradle/*" 2>/dev/null | head -5)

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

echo -e "${BLUE}检测到设备:${NC}"
adb devices | grep "device$" | sed 's/^/  /'
echo ""

# 执行安装
echo -e "${BLUE}安装 APK: $APK_PATH${NC}"
if adb install -r "$APK_PATH"; then
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  安装成功！${NC}"
    echo -e "${GREEN}========================================${NC}"

    # 启动应用
    echo ""
    echo -e "${BLUE}启动应用...${NC}"
    adb shell am start -n "com.example.weiqigame/.MainActivity"
else
    echo ""
    echo -e "${RED}========================================${NC}"
    echo -e "${RED}  安装失败${NC}"
    echo -e "${RED}========================================${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}完成！${NC}"
