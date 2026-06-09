# Agent Guide

## 项目定位

原生 Android AI 聊天应用，本地优先存储，聚焦聊天和图片生成。详细背景看 [README.md](README.md) 和 [docs/优化路线图.md](docs/优化路线图.md)。

## 技术栈

- Android：Kotlin、Jetpack Compose、Material 3、Room、Koin、OkHttp、kotlinx.serialization、Baseline Profile。
- 版本以 [gradle/libs.versions.toml](gradle/libs.versions.toml)、[app/build.gradle.kts](app/build.gradle.kts) 为准。

## 主要目录

- [app/](app/)：Android 应用。
- [baselineprofile/](baselineprofile/)：Baseline Profile 测试模块。
- [.github/workflows/](.github/workflows/)：CI。
- [docs/](docs/)：项目文档；docs 目录下的文件名必须使用中文，方便人工查看。

## Android 约定

主代码：[app/src/main/java/com/aichat/workbench/](app/src/main/java/com/aichat/workbench/)

- [app/](app/src/main/java/com/aichat/workbench/app/)：Application、Koin DI。
- [data/](app/src/main/java/com/aichat/workbench/data/)：Room、DAO、entity、mapper、repository、SecretStore。
- [domain/](app/src/main/java/com/aichat/workbench/domain/)：model、repository 接口、usecase。
- [feature/](app/src/main/java/com/aichat/workbench/feature/)：Compose screen、ViewModel、UiState。
- [provider/](app/src/main/java/com/aichat/workbench/provider/)：OpenAI / compatible provider。

规则：

- UI 状态放 ViewModel/UiState，Compose 保持声明式。
- data entity 不直接泄漏到 domain/feature，使用 mapper。
- 新依赖注册到 [AppModule.kt](app/src/main/java/com/aichat/workbench/app/AppModule.kt)。
- Room 当前 schema version 18，以 [AiChatDatabase.kt](app/src/main/java/com/aichat/workbench/data/local/AiChatDatabase.kt) 为准；改 entity/DAO/Database 时同步 migration、schema JSON 和迁移测试。

## 常用命令

```powershell
.\gradlew.bat testDebugUnitTest lint assembleDebug --no-daemon --stacktrace
```

## 验证要求

- Android 改动：至少跑相关单测；涉及 UI/资源/构建配置时跑 lint 或 assembleDebug。
- Room 改动：跑数据库测试并确认 schema JSON 更新。

CI：Android 看 [.github/workflows/android.yml](.github/workflows/android.yml)。

## 发布规则

- 不为单个小 UI 调整、文案调整、状态标签、扫视性优化创建 GitHub Release 或 tag。
- 只有重大更新才允许发版：完整功能闭环、重要架构变更、数据库/协议升级、可安装包修复、安全修复或用户明确要求发布。
- 多个小改动应先合并到 `main`，等形成一个可说明的里程碑后再统一升级版本号、打 tag、创建 Release。
- 发布前必须确认安装包可安装；禁止把 `app-release-unsigned.apk` 作为 GitHub Release 安装包上传。
- 没有正式签名配置时，不发布 release APK；如临时需要给用户安装测试，必须明确产物类型并使用可安装的 debug APK 或已签名 APK。
- Release notes 必须说明用户可感知的重大变化、验证命令和安装包类型。

## 行为边界

- 先检查代码、文档、测试和当前 git diff，再修改。
- 只做必要改动，不自动重构无关文件。
- 不自动 commit、push 或改 git config，除非用户明确要求。
- 不提交密钥、真实 API Key、签名材料或敏感导出数据。
