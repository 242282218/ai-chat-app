#!/bin/bash
# Phase 2B 快速验证脚本
# 用于快速检查基本功能是否正常

set -e

echo "╔═══════════════════════════════════════════════════════════╗"
echo "║       Stream Chat UI - Phase 2B 快速验证脚本             ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查点
check_pass() {
    echo -e "${GREEN}✓${NC} $1"
}

check_fail() {
    echo -e "${RED}✗${NC} $1"
}

check_warn() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# 1. 检查环境
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "1️⃣  检查测试环境"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 检查 adb
if command -v adb &> /dev/null; then
    check_pass "adb 已安装"

    # 检查设备连接
    DEVICE_COUNT=$(adb devices | grep -v "List" | grep "device" | wc -l)
    if [ $DEVICE_COUNT -gt 0 ]; then
        check_pass "发现 $DEVICE_COUNT 个设备"
        adb devices
    else
        check_fail "未发现连接的设备"
        echo "  请连接 Android 设备或启动模拟器"
        exit 1
    fi
else
    check_fail "adb 未安装"
    echo "  请安装 Android SDK Platform Tools"
    exit 1
fi

# 2. 构建 APK
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "2️⃣  构建 Debug APK"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

./gradlew assembleDebug

if [ $? -eq 0 ]; then
    check_pass "构建成功"

    # 检查 APK 大小
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
    if [ -f "$APK_PATH" ]; then
        APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
        echo "  APK 大小: $APK_SIZE"

        # 提取数字部分（假设是 MB）
        APK_SIZE_NUM=$(ls -l "$APK_PATH" | awk '{print $5}')
        APK_SIZE_MB=$(echo "scale=2; $APK_SIZE_NUM / 1024 / 1024" | bc)

        if (( $(echo "$APK_SIZE_MB <= 32.5" | bc -l) )); then
            check_pass "APK 体积合格 (≤ 32.5MB)"
        elif (( $(echo "$APK_SIZE_MB <= 35" | bc -l) )); then
            check_warn "APK 体积可接受 (32.5-35MB)"
        else
            check_fail "APK 体积超标 (> 35MB)"
        fi
    else
        check_fail "APK 文件不存在"
        exit 1
    fi
else
    check_fail "构建失败"
    exit 1
fi

# 3. 安装 APK
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "3️⃣  安装应用到设备"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

adb install -r "$APK_PATH"

if [ $? -eq 0 ]; then
    check_pass "安装成功"
else
    check_fail "安装失败"
    exit 1
fi

# 4. 测试启动
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "4️⃣  测试应用启动"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 测量启动时间
LAUNCH_OUTPUT=$(adb shell am start -W com.aichat.workbench/.MainActivity)
echo "$LAUNCH_OUTPUT"

TOTAL_TIME=$(echo "$LAUNCH_OUTPUT" | grep "TotalTime" | awk '{print $2}')
if [ ! -z "$TOTAL_TIME" ]; then
    echo "  启动时间: ${TOTAL_TIME}ms"

    if [ $TOTAL_TIME -le 2000 ]; then
        check_pass "启动时间合格 (≤ 2000ms)"
    elif [ $TOTAL_TIME -le 2400 ]; then
        check_warn "启动时间可接受 (2000-2400ms)"
    else
        check_fail "启动时间超标 (> 2400ms)"
    fi
else
    check_warn "无法测量启动时间"
fi

# 5. 检查日志
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "5️⃣  检查应用错误日志"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

sleep 2  # 等待启动

# 清空旧日志
adb logcat -c

# 重启应用
adb shell am force-stop com.aichat.workbench
sleep 1
adb shell am start com.aichat.workbench/.MainActivity

sleep 2

# 捕获崩溃/异常日志。本地优先实验入口默认不连接 Stream Cloud，因此不再检查 Stream 初始化日志。
LOGS=$(adb logcat -d -s "AndroidRuntime:E" "System.err:W")

ERROR_COUNT=$(echo "$LOGS" | grep -E "FATAL EXCEPTION|RuntimeException|IllegalStateException" | wc -l)
if [ $ERROR_COUNT -eq 0 ]; then
    check_pass "未发现启动崩溃日志"
else
    check_fail "发现 $ERROR_COUNT 个崩溃/异常日志"
    echo "$LOGS" | grep -E "FATAL EXCEPTION|RuntimeException|IllegalStateException"
fi

# 6. 性能基准
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "6️⃣  性能基准数据"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 内存占用
sleep 3
MEMINFO=$(adb shell dumpsys meminfo com.aichat.workbench | grep "TOTAL" | head -1)
echo "  内存占用: $MEMINFO"

# 总结
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ 快速验证完成"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📝 下一步："
echo "  1. 打开 docs/implementation-guide/阶段2B-验证待办.md"
echo "  2. 按清单逐项手动测试功能"
echo "  3. 记录评分和问题"
echo ""
echo "💡 提示："
echo "  - 实时日志: adb logcat -s 'StreamChat*:*'"
echo "  - 内存监控: adb shell dumpsys meminfo com.aichat.workbench"
echo "  - 性能监控: adb shell dumpsys gfxinfo com.aichat.workbench"
echo ""
