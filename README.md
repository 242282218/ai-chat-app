# AI Chat

本项目是一个原生 Android AI 聊天工作台，采用本地优先存储，并提供可选的 Go 工具网关。

## 当前范围

- Android App：Kotlin、Jetpack Compose、Material 3、Room、OkHttp。
- Provider：支持 OpenAI、OpenAI-compatible 文本聊天，以及 OpenAI 图片生成。
- 本地数据：会话、消息、Prompt、模型偏好、Provider 配置、工具结果、图片历史。
- 隐私：API Key 通过 Android Keystore 支持的存储保存；备份导出不包含 API Key，默认不导出敏感会话和临时会话。
- 工具：可选 Go 网关，支持健康检查、工具清单、网页搜索和 Python 沙箱执行。
- 数据管理：Settings 页面支持 JSON 导入导出，以及清空聊天、Provider/API Key、Prompt/模型偏好/图片或全部本地数据。

## Android

下载测试版 APK：

- GitHub Releases：<https://github.com/242282218/ai-chat-app/releases>

当前 Release 中的 APK 使用测试签名，适合安装体验；后续如果切换正式签名，可能需要先卸载旧版本再安装。

前置要求：

- JDK 17。
- Android SDK，包含 platform 36。

构建和验证：

```powershell
.\gradlew.bat testDebugUnitTest lint assembleDebug --no-daemon --stacktrace
```

可以从 Android Studio 运行 debug App，也可以安装生成的 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

模拟器访问本地网关时，默认网关地址使用：

```text
http://10.0.2.2:8080
```

## Gateway

前置要求：

- Go 1.26.x。
- Docker，用于 Python 沙箱接口。

运行测试：

```powershell
cd gateway
go test ./...
```

本地启动：

```powershell
cd gateway
go run .\cmd\gateway
```

网关默认监听 `:8080`。可以用环境变量覆盖：

```powershell
$env:GATEWAY_ADDR = ":8081"
```

接口：

- `GET /health`
- `GET /v1/tools/manifest`
- `POST /v1/search`
- `POST /v1/sandbox/run`

沙箱通过 Docker 执行，默认禁用网络，并限制 CPU、内存、进程数，容器文件系统只读，同时会截断过长输出。

## 验证

最近一次本地验证：

```powershell
.\gradlew.bat testDebugUnitTest lint assembleDebug --no-daemon --stacktrace
cd gateway
go test ./...
```

当前验收证据和已知限制见 `docs/reports/2026-05-31-mvp-acceptance.md`。
