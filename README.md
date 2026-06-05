# AI Chat

本项目是一个原生 Android AI 聊天工作台，采用本地优先存储，并提供可选的 Go 工具网关。

## 当前范围

- Android App：Kotlin、Jetpack Compose、Material 3、Room、OkHttp。
- Provider：支持 OpenAI、OpenAI-compatible、New API、Sub2 API、自定义兼容接口文本聊天，以及 OpenAI 兼容图片生成。
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
.\gradlew.bat testDebugUnitTest lint assembleDebug assembleRelease --no-daemon --stacktrace
```

GitHub Actions 会执行同等 Android 检查并上传测试报告、lint 报告、APK 和 release mapping artifact。外部 Provider 连接测试使用 GitHub Secrets 或 `.env.example` 中的占位变量，不在代码中硬编码 API Key。

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

- Go 1.26.4 或更新的 1.26 patch 版本。
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

网关默认监听 `127.0.0.1:8080`。可以用环境变量覆盖：

```powershell
$env:GATEWAY_ADDR = "127.0.0.1:8081"
```

`/v1/search` 和 `/v1/sandbox/run` 需要配置 `GATEWAY_API_TOKEN`，客户端请求使用 `Authorization: Bearer <token>`。

接口：

- `GET /health`
- `GET /v1/tools/manifest`
- `POST /v1/search`
- `POST /v1/sandbox/run`

沙箱通过 Docker 执行，默认禁用网络，并限制 CPU、内存、进程数，容器文件系统只读，同时会截断过长输出。

## 验证

最近一次本地验证：

```powershell
.\gradlew.bat testDebugUnitTest lint assembleDebug assembleRelease --no-daemon --stacktrace
cd gateway
go test ./...
```

当前优化优先级和已知风险见 `docs/plans/2026-06-04-移动端codex工作台重构蓝图.md`。

## 隐私边界

- App 默认关闭 Android Auto Backup，不把 Room 数据库、图片文件和 SharedPreferences 交给系统云备份或设备迁移。
- API Key 通过 Android Keystore 支持的本地存储保存；应用内 JSON 导出不会包含 API Key。
- 聊天内容默认只保存在本机；发送消息、图片或工具请求时，相关内容会发往用户配置的 Provider 或 Gateway。
- 如需迁移数据，请使用 Settings 页面里的导出/导入功能；敏感会话和临时会话默认不随聊天备份导出。
