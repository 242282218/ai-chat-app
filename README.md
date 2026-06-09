# AI Chat

本项目是一个原生 Android AI 聊天应用，采用本地优先存储，聚焦聊天和图片生成。

## 当前范围

- Android App：Kotlin、Jetpack Compose、Material 3、Room、DataStore、Retrofit、OkHttp。
- Provider：支持 OpenAI、OpenAI-compatible、New API、Sub2 API、自定义兼容接口文本聊天，以及 OpenAI 兼容图片生成。
- 本地数据：会话、消息、Provider 配置、聊天/图片模型选择、图片历史。
- 隐私：API Key 通过 Android Keystore 支持的存储保存；App 默认关闭 Android Auto Backup。
- 数据管理：本地保存聊天、Provider/API Key、聊天/图片模型选择和图片生成记录。

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

图片生成配置：

- 在设置页添加一个支持图片生成的 Provider，例如 New API 或自定义兼容接口。
- `API_BASE_URL` 对应 Provider 的 Base URL；根地址会自动规范化为 `/v1`。
- `IMAGE_API_KEY` 对应该图片 Provider 的 API Key，保存到 Android Keystore 支持的本地安全存储。
- 图片生成固定调用 OpenAI-compatible `POST /v1/images/generations`，聊天 Provider 和图片 Provider 可以分开配置。

## 验证

最近一次本地验证：

```powershell
.\gradlew.bat testDebugUnitTest lint assembleDebug assembleRelease --no-daemon --stacktrace
```

当前目录、架构、核心模型和页面代码入口见 `docs/基础架构与代码索引.md`。

## 隐私边界

- App 默认关闭 Android Auto Backup，不把 Room 数据库、图片文件和 SharedPreferences 交给系统云备份或设备迁移。
- API Key 通过 Android Keystore 支持的本地存储保存。
- 聊天内容默认只保存在本机；发送消息或图片请求时，相关内容会发往用户配置的 Provider。
