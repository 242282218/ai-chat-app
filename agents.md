# Agent Guide

## 项目定位

原生 Android AI 聊天工作台，本地优先存储，可选 Go 工具网关。详细背景看 [README.md](README.md) 和 [docs/优化路线图.md](docs/优化路线图.md)。

## 技术栈

- Android：Kotlin、Jetpack Compose、Material 3、Room、Koin、OkHttp、kotlinx.serialization、Baseline Profile。
- Gateway：Go 1.26，标准库 HTTP server，提供 health、tool manifest、search、sandbox API。
- 版本以 [gradle/libs.versions.toml](gradle/libs.versions.toml)、[app/build.gradle.kts](app/build.gradle.kts)、[gateway/go.mod](gateway/go.mod) 为准。

## 主要目录

- [app/](app/)：Android 应用。
- [baselineprofile/](baselineprofile/)：Baseline Profile 测试模块。
- [gateway/](gateway/)：Go 工具网关。
- [contracts/gateway/](contracts/gateway/)：Android/Gateway 协议样例。
- [.github/workflows/](.github/workflows/)：CI。
- [docs/](docs/)：项目文档；docs 目录下的文件名必须使用中文，方便人工查看。

## Android 约定

主代码：[app/src/main/java/com/aichat/workbench/](app/src/main/java/com/aichat/workbench/)

- [app/](app/src/main/java/com/aichat/workbench/app/)：Application、Koin DI。
- [data/](app/src/main/java/com/aichat/workbench/data/)：Room、DAO、entity、mapper、repository、SecretStore、backup。
- [domain/](app/src/main/java/com/aichat/workbench/domain/)：model、repository 接口、usecase。
- [feature/](app/src/main/java/com/aichat/workbench/feature/)：Compose screen、ViewModel、UiState。
- [provider/](app/src/main/java/com/aichat/workbench/provider/)：OpenAI / compatible provider。
- [tool/](app/src/main/java/com/aichat/workbench/tool/)：Gateway client 和工具模型。

规则：

- UI 状态放 ViewModel/UiState，Compose 保持声明式。
- data entity 不直接泄漏到 domain/feature，使用 mapper。
- 新依赖注册到 [AppModule.kt](app/src/main/java/com/aichat/workbench/app/AppModule.kt)。
- Room 当前 schema version 7；改 entity/DAO/Database 时同步 migration、schema JSON 和迁移测试。

## Gateway 约定

主代码：[gateway/](gateway/)

- [gateway/cmd/gateway/](gateway/cmd/gateway/)：入口。
- [gateway/internal/config/](gateway/internal/config/)：环境变量配置。
- [gateway/internal/httpapi/](gateway/internal/httpapi/)：HTTP API。
- [gateway/internal/search/](gateway/internal/search/)：search adapter。
- [gateway/internal/sandbox/](gateway/internal/sandbox/)：Docker sandbox。

规则：

- Go error 必须带上下文：`fmt.Errorf("doX: %w", err)`。
- API 错误保持结构化 JSON。
- 新增工具时同步 [contracts/gateway/fixtures/tool-manifest.json](contracts/gateway/fixtures/tool-manifest.json)、Android client/model 和测试。
- Sandbox 默认保持禁用网络、资源限制、只读文件系统和输出截断。

## 常用命令

```powershell
.\gradlew.bat testDebugUnitTest lint assembleDebug --no-daemon --stacktrace
```

```powershell
cd gateway
go test ./...
go run .\cmd\gateway
```

模拟器访问本机网关：`http://10.0.2.2:8080`。

## 验证要求

- Android 改动：至少跑相关单测；涉及 UI/资源/构建配置时跑 lint 或 assembleDebug。
- Room 改动：跑数据库测试并确认 schema JSON 更新。
- Gateway 改动：跑 `go test ./...`。
- 跨端协议改动：同时更新 contracts fixture，并跑 Android/Gateway 相关测试。

CI：Android 看 [.github/workflows/android.yml](.github/workflows/android.yml)，Gateway 看 [.github/workflows/gateway.yml](.github/workflows/gateway.yml)。

## 行为边界

- 先检查代码、文档、测试和当前 git diff，再修改。
- 只做必要改动，不自动重构无关文件。
- 不自动 commit、push 或改 git config，除非用户明确要求。
- 不提交密钥、真实 API Key、签名材料或敏感导出数据。
