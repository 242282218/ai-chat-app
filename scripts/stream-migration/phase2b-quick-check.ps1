# Phase 2B 快速验证脚本 (PowerShell)
# 用于快速检查基本功能是否正常

Write-Host "╔═══════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║       Stream Chat UI - Phase 2B 快速验证脚本             ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

function Check-Pass {
    param([string]$Message)
    Write-Host "✓ $Message" -ForegroundColor Green
}

function Check-Fail {
    param([string]$Message)
    Write-Host "✗ $Message" -ForegroundColor Red
}

function Check-Warn {
    param([string]$Message)
    Write-Host "⚠ $Message" -ForegroundColor Yellow
}

# 1. 检查环境
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "1️⃣  检查测试环境" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan

# 检查 adb
if (Get-Command adb -ErrorAction SilentlyContinue) {
    Check-Pass "adb 已安装"

    # 检查设备连接
    $devices = adb devices | Select-String "device$"
    if ($devices.Count -gt 0) {
        Check-Pass "发现 $($devices.Count) 个设备"
        adb devices
    } else {
        Check-Fail "未发现连接的设备"
        Write-Host "  请连接 Android 设备或启动模拟器" -ForegroundColor Yellow
        exit 1
    }
} else {
    Check-Fail "adb 未安装"
    Write-Host "  请安装 Android SDK Platform Tools" -ForegroundColor Yellow
    exit 1
}

# 2. 构建 APK
Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "2️⃣  构建 Debug APK" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan

.\gradlew.bat assembleDebug

if ($LASTEXITCODE -eq 0) {
    Check-Pass "构建成功"

    # 检查 APK 大小
    $apkPath = "app\build\outputs\apk\debug\app-debug.apk"
    if (Test-Path $apkPath) {
        $apkSize = (Get-Item $apkPath).Length / 1MB
        Write-Host "  APK 大小: $([math]::Round($apkSize, 2)) MB"

        if ($apkSize -le 32.5) {
            Check-Pass "APK 体积合格 (≤ 32.5MB)"
        } elseif ($apkSize -le 35) {
            Check-Warn "APK 体积可接受 (32.5-35MB)"
        } else {
            Check-Fail "APK 体积超标 (> 35MB)"
        }
    } else {
        Check-Fail "APK 文件不存在"
        exit 1
    }
} else {
    Check-Fail "构建失败"
    exit 1
}

# 3. 安装 APK
Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "3️⃣  安装应用到设备" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan

adb install -r $apkPath

if ($LASTEXITCODE -eq 0) {
    Check-Pass "安装成功"
} else {
    Check-Fail "安装失败"
    exit 1
}

# 4. 测试启动
Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "4️⃣  测试应用启动" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan

# 测量启动时间
$launchOutput = adb shell am start -W com.aichat.workbench/.MainActivity
Write-Host $launchOutput

if ($launchOutput -match "TotalTime: (\d+)") {
    $totalTime = [int]$matches[1]
    Write-Host "  启动时间: ${totalTime}ms"

    if ($totalTime -le 2000) {
        Check-Pass "启动时间合格 (≤ 2000ms)"
    } elseif ($totalTime -le 2400) {
        Check-Warn "启动时间可接受 (2000-2400ms)"
    } else {
        Check-Fail "启动时间超标 (> 2400ms)"
    }
} else {
    Check-Warn "无法测量启动时间"
}

# 5. 检查日志
Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "5️⃣  检查应用错误日志" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan

Start-Sleep -Seconds 2

# 清空旧日志
adb logcat -c

# 重启应用
adb shell am force-stop com.aichat.workbench
Start-Sleep -Seconds 1
adb shell am start com.aichat.workbench/.MainActivity

Start-Sleep -Seconds 2

# 捕获崩溃/异常日志。本地优先实验入口默认不连接 Stream Cloud，因此不再检查 Stream 初始化日志。
$logs = adb logcat -d -s "AndroidRuntime:E" "System.err:W"
$errorCount = ($logs | Select-String -Pattern "FATAL EXCEPTION|RuntimeException|IllegalStateException").Count
if ($errorCount -eq 0) {
    Check-Pass "未发现启动崩溃日志"
} else {
    Check-Fail "发现 $errorCount 个崩溃/异常日志"
    $logs | Select-String -Pattern "FATAL EXCEPTION|RuntimeException|IllegalStateException"
}

# 6. 性能基准
Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "6️⃣  性能基准数据" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan

Start-Sleep -Seconds 3
$meminfo = adb shell dumpsys meminfo com.aichat.workbench | Select-String "TOTAL" | Select-Object -First 1
Write-Host "  内存占用: $meminfo"

# 总结
Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "✅ 快速验证完成" -ForegroundColor Green
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host ""
Write-Host "📝 下一步：" -ForegroundColor Yellow
Write-Host "  1. 打开 docs/implementation-guide/阶段2B-验证待办.md"
Write-Host "  2. 按清单逐项手动测试功能"
Write-Host "  3. 记录评分和问题"
Write-Host ""
Write-Host "💡 提示：" -ForegroundColor Yellow
Write-Host "  - 实时日志: adb logcat -s 'StreamChat*:*'"
Write-Host "  - 内存监控: adb shell dumpsys meminfo com.aichat.workbench"
Write-Host "  - 性能监控: adb shell dumpsys gfxinfo com.aichat.workbench"
Write-Host ""
