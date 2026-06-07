# 多 agent 项目审查与优化建议

审查日期：2026-06-06

审查范围：`AGENTS.md`、`README.md`、`docs/`、当前 git diff、`app/`、`gateway/`、`contracts/gateway/`、`.github/workflows/`。当前业务代码没有未提交 diff，仅看到 `.claude/scheduled_tasks.lock` 删除；本报告不把它计入项目问题。

本地验证限制：已用本机 JDK 17 和 Android SDK 重新尝试 Android Gradle；当前失败点是 Gradle 解析 `dl.google.com` 依赖时 TLS handshake 被远端关闭。因此 Android 构建类结论需要在网络可访问 Maven 仓库的 JDK 17/Android SDK 环境或 CI 中确认。

## 当前项目总体判断

项目方向清晰：原生 Android 本地优先 AI 聊天工作台，Go Gateway 作为可选工具网关。Android 侧已经有 Compose、Room、Provider、工具和图片生成的分层；Gateway 侧已有 token、请求体限制、Docker sandbox 资源限制；CI 已覆盖 Android 单测/lint/build 和 Gateway test/race/vet/govulncheck。

当前最值得优化的不是“重写架构”，而是补齐几个会直接影响稳定性、安全边界和协议演进的缺口：工具失败路径疑似编译问题、mock 搜索默认暴露、聊天取消持久化、大文本导航传递、敏感草稿保存、跨端协议 CI 闭环。

## 多 agent 审查结论

- 架构 agent：整体分层可用，但 `ToolExecutor` 已经把 Gateway、本地工具、图片生成、设置和敏感字段处理聚合到 `domain`，后续工具增长会继续放大边界压力。
- Android agent：核心风险在生命周期和状态传递，特别是生成取消、长草稿路由、密钥草稿保存；测试覆盖已有基础，不应泛泛要求“多写测试”。
- Gateway agent：网关安全默认值较好，但默认 `mock` search 容易污染真实聊天结果；协议 fixture 还未覆盖 search/sandbox/error。
- 质量 agent：CI 基础不错，但 `contracts/gateway/**` 变更不会自动触发 Android Gateway client 兼容性测试；发布 debug APK 的语义需要更硬。
- 开源对标 agent：同类项目普遍把 Provider 配置、本地隐私说明、工具/搜索开关、API server 安全配置和协议兼容作为显式产品能力，本项目可以借鉴这些边界表达，但不应搬入重型服务端平台能力。

## 开源项目对标

1. [GPT Mobile](https://github.com/Taewan-P/gpt_mobile)
   - 核心做法：原生 Android，Kotlin + Jetpack Compose，支持多 Provider、自定义 API URL/模型、本地聊天历史。
   - 可借鉴点：Provider 配置体验、本地保存边界说明、多模型对比回答。
   - 不要照搬：它偏纯聊天客户端，不覆盖 Gateway、sandbox、工具执行链。

2. [Chatbox](https://github.com/chatboxai/chatbox)
   - 核心做法：跨平台 AI 客户端，多 Provider、本地数据存储、Prompt Library、Markdown/LaTeX、流式回复。
   - 可借鉴点：Provider onboarding、Prompt 管理、导入导出说明和发布文案。
   - 不要照搬：Electron/React 架构和云同步能力不适合当前原生 Android 第一阶段。

3. [Open WebUI](https://github.com/open-webui/open-webui)
   - 核心做法：自托管 AI 平台，支持 Ollama/OpenAI-compatible API、RAG、Web Search、多搜索 Provider、Python tool。
   - 可借鉴点：工具注册表、搜索 Provider 插件化、搜索结果来源和截断策略。
   - 不要照搬：RBAC、向量库矩阵、企业部署能力对本项目过重。

4. [LibreChat](https://github.com/danny-avila/LibreChat)
   - 核心做法：自托管多 Provider ChatGPT 替代，支持 custom endpoints、Agents、MCP、Code Interpreter、Web Search。
   - 可借鉴点：工具返回结构统一、Agent/tool 通过标准接口暴露、可恢复流式体验。
   - 不要照搬：多用户认证、Agent Marketplace、企业配置复杂度。

5. [Jan](https://github.com/janhq)
   - 核心做法：本地优先 ChatGPT 替代，提供 OpenAI-compatible 本地 API server、API key、trusted hosts、server logs。
   - 可借鉴点：Gateway 安全配置产品化：默认本机监听、必须 token、trusted hosts、工具执行开关和可读日志。
   - 不要照搬：本地模型下载、推理服务和桌面运行时不是当前 Android 项目核心。

## 高优先级优化项

### H1. 修复 `ToolExecutor.saveFailure()` 失败路径

- 问题：`saveFailure()` 内使用 `toolDescriptor?.sensitiveInputFields`，但该函数作用域内没有 `toolDescriptor`。
- 证据文件：`app/src/main/java/com/aichat/workbench/domain/tool/ToolExecutor.kt`；`saveFailure()` 附近；同文件成功路径使用 `toolDescriptor.sensitiveInputFields`。
- 风险：Android 可能直接编译失败；即使后续修成可编译，工具失败路径也可能无法按 descriptor 脱敏原始输入。
- 最小改动方案：给 `saveFailure()` 增加 `sensitiveInputFields: Set<String> = emptySet()` 参数；有 descriptor 的调用传 `toolDescriptor.sensitiveInputFields`，未知工具等路径使用默认空集合。
- 验证命令：`.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon --stacktrace`

### H2. Gateway 默认搜索不要返回 mock 结果

- 问题：`SEARCH_PROVIDER` 默认是 `mock`，只要配置了 `GATEWAY_API_TOKEN`，`/v1/search` 就会返回 `example.com` 示例结果。
- 证据文件：`gateway/internal/config/config.go`、`gateway/cmd/gateway/main.go`、`gateway/internal/search/search.go`、`app/src/main/java/com/aichat/workbench/domain/tool/ToolExecutor.kt`。
- 风险：聊天工具 `web_search` 会把 mock 来源写入工具结果，用户可能误以为是真实检索。
- 最小改动方案：生产启动要求显式配置 `SEARCH_PROVIDER=searxng`；`mock` 仅限测试/开发模式。或者当 provider 为 mock 时不暴露 `web_search` manifest，并让 `/v1/search` 返回 `search_unavailable`。
- 验证命令：`cd gateway; go test ./...`

### H3. 生成被页面销毁取消时要持久化 `Cancelled`

- 问题：用户返回或 ViewModel 清理时，生成 coroutine 可能被取消，但现有明确持久化 `Cancelled` 的路径主要在用户点击“停止生成”。
- 证据文件：`app/src/main/java/com/aichat/workbench/feature/chat/ChatViewModel.kt`、`app/src/main/java/com/aichat/workbench/feature/chat/GenerationController.kt`、`app/src/main/java/com/aichat/workbench/domain/usecase/SendMessageUseCase.kt`。
- 风险：历史消息可能停留在 `Pending` 或 `Streaming`，造成“永远生成中”的脏状态。
- 最小改动方案：给 `GenerationController` 增加清理取消方法，在 `ChatViewModel.onCleared()` 调用；或在 `SendMessageUseCase` 捕获取消前保存当前 assistant 内容为 `Cancelled`。补 ViewModel 清理单测。
- 验证命令：`.\gradlew.bat :app:testDebugUnitTest --tests "*ChatViewModelTest*" --tests "*SendMessageUseCaseTest*" --no-daemon --stacktrace`

### H4. 大草稿不要通过导航 route 传递

- 问题：工具/图片结果“带入聊天”通过 route query 传完整 draft，工具结果可能包含长 JSON、日志和错误输出。
- 证据文件：`app/src/main/java/com/aichat/workbench/navigation/AppNavHost.kt`、`app/src/main/java/com/aichat/workbench/feature/tools/ToolsScreen.kt`、`app/src/main/java/com/aichat/workbench/feature/chat/ToolInstructionBuilder.kt`。
- 风险：长文本放大到 route/saved state 后，可能触发导航失败、状态恢复异常或页面卡顿。
- 最小改动方案：引入轻量 `DraftHandoffRepository`，route 只传短 ID；超长 draft 写入内存仓库或临时 Room 表。过渡期先加长度限制，超限走 handoff。
- 验证命令：`.\gradlew.bat :app:testDebugUnitTest --tests "*DraftStateTest*" --tests "*ChatViewModelTest*" --no-daemon --stacktrace`

### H5. 密钥/token 草稿不要进入 saveable 状态

- 问题：Provider API Key 和 Gateway/search token 草稿可能保存在 `rememberSaveable` 或 ViewModel UI state 中。
- 证据文件：`app/src/main/java/com/aichat/workbench/feature/provider/ProviderSettingsScreen.kt`、`app/src/main/java/com/aichat/workbench/feature/tools/ToolsViewModel.kt`、`app/src/main/java/com/aichat/workbench/feature/tools/ToolsScreen.kt`。
- 风险：明文密钥可能进入 Activity saved state；虽然不是 Room 明文，但与“Key 只进入 SecretStore”的隐私边界不完全一致。
- 最小改动方案：密钥输入改为不可 saveable 的一次性草稿；ViewModel 只保留 `hasKey`、`hasToken` 和保存状态，保存成功后立即清空明文。旋转屏幕时让用户重输未保存密钥。
- 验证命令：`.\gradlew.bat :app:testDebugUnitTest --tests "*ProviderDraftsTest*" --tests "*ToolsViewModelTest*" --no-daemon --stacktrace`

### H6. 协议变更必须同时触发 Android 和 Gateway 验证

- 问题：Android workflow 没监听 `contracts/gateway/**`，但 Android `GatewayClientTest` 会读取 Gateway fixture。
- 证据文件：`.github/workflows/android.yml`、`.github/workflows/gateway.yml`、`app/src/test/kotlin/com/aichat/workbench/tool/gateway/GatewayClientTest.kt`、`contracts/gateway/fixtures/tool-manifest.json`。
- 风险：协议 fixture 或 schema 改动只跑 Gateway CI，Android client 解析回归可能漏到运行期。
- 最小改动方案：把 `contracts/gateway/**` 加入 Android workflow paths；或新增 `contract.yml`，同时跑 Android `GatewayClientTest` 和 Gateway `go test ./...`。
- 验证命令：`.\gradlew.bat :app:testDebugUnitTest --tests "*GatewayClientTest*" --no-daemon --stacktrace`；`cd gateway; go test ./...`

## 中低优先级优化项

### M1. 收敛 `ToolExecutor` 架构边界

- 问题：`ToolExecutor` 位于 `domain/tool`，但依赖 Gateway、local tool、provider、settings、image generation、repository 和 sanitizer。
- 证据文件：`app/src/main/java/com/aichat/workbench/domain/tool/ToolExecutor.kt`、`docs/基础架构与代码索引.md`。
- 风险：新增工具会继续把网络、存储和执行策略塞进 domain，降低可测试性和替换能力。
- 最小改动方案：domain 只保留接口和模型，例如 `ToolExecutionService`；当前实现迁移到 `tool/runtime`，先移动文件和 DI，不改行为。
- 验证命令：`.\gradlew.bat :app:testDebugUnitTest --no-daemon --stacktrace`

### M2. 扩展 Gateway golden fixture

- 问题：当前主要有 `tool-manifest.json` fixture，search/sandbox/error 还没有同等双端 golden fixture。
- 证据文件：`contracts/gateway/fixtures/tool-manifest.json`、`gateway/internal/httpapi/server_test.go`、`app/src/test/kotlin/com/aichat/workbench/tool/gateway/GatewayClientTest.kt`、`contracts/gateway/README.md`。
- 风险：search/sandbox/error 的字段兼容性主要靠分散测试，跨端协议漂移不够早发现。
- 最小改动方案：新增 `search-response.json`、`sandbox-run-response.json`、`gateway-error.json`；Go 输出测试和 Android 解析测试都读取同一 fixture。
- 验证命令：`cd gateway; go test ./...`；`.\gradlew.bat :app:testDebugUnitTest --tests "*GatewayClientTest*" --no-daemon --stacktrace`

### M3. 更新过期文档和根目录报告归档

- 问题：项目规范写 Room schema version 7，实际数据库是 version 10；根目录存在大量英文阶段报告，入口噪声较高。
- 证据文件：`AGENTS.md`、`app/src/main/java/com/aichat/workbench/data/local/AiChatDatabase.kt`、`docs/基础架构与代码索引.md`、根目录 `FINAL_*`、`COMPLETE_*`、`H*_*.md` 等文件。
- 风险：后续改 Room 时容易按错误版本补 migration；新人阅读项目入口时难以判断哪些文档仍有效。
- 最小改动方案：`AGENTS.md` 改为 version 10，并注明以 `AiChatDatabase.kt` 为准；阶段报告后续统一移动到 `docs/归档/`，文件名改中文，本次不建议顺手批量移动。
- 验证命令：`rg -n "schema version|Room 当前|version = 10" AGENTS.md docs app/src/main/java/com/aichat/workbench/data/local/AiChatDatabase.kt`

### M4. 拆分过长 UI 和测试文件

- 问题：`ChatScreen.kt`、`ToolsScreen.kt`、`ToolsViewModel.kt`、`AiChatDatabaseTest.kt` 文件过长，维护成本上升。
- 证据文件：`app/src/main/java/com/aichat/workbench/feature/chat/ChatScreen.kt`、`app/src/main/java/com/aichat/workbench/feature/tools/ToolsScreen.kt`、`app/src/main/java/com/aichat/workbench/feature/tools/ToolsViewModel.kt`、`app/src/test/kotlin/com/aichat/workbench/data/local/AiChatDatabaseTest.kt`。
- 风险：小改动容易误触相邻逻辑，review 成本高；数据库测试混合 migration、repository、backup 和 usecase 后定位失败慢。
- 最小改动方案：只移动私有 Composable、格式化函数和测试分组，不改行为；数据库测试拆为 `MigrationTest`、`RepositoryTest`、`BackupServiceTest`。
- 验证命令：`.\gradlew.bat :app:testDebugUnitTest :app:lint --no-daemon --stacktrace`

### M5. 增加设备测试和 Baseline Profile 验证入口

- 问题：已有 `androidTest` 和 `baselineprofile` 模块，但常规 CI 没跑设备测试或 profile 生成。
- 证据文件：`app/src/androidTest/java/com/aichat/workbench/tool/local/AndroidJavaScriptRunnerDeviceTest.kt`、`baselineprofile/src/main/java/com/aichat/workbench/baselineprofile/BaselineProfileGenerator.kt`、`.github/workflows/android.yml`。
- 风险：JavaScriptSandbox、真实设备权限、启动性能路径只能靠人工发现。
- 最小改动方案：新增手动或夜间 workflow 跑 `connectedDebugAndroidTest`；发布前跑 baseline profile 生成。先不放进每次 PR，避免拖慢主 CI。
- 验证命令：`.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --stacktrace`；`.\gradlew.bat :baselineprofile:connectedNonMinifiedReleaseAndroidTest --no-daemon --stacktrace`

### M6. 发布 debug APK 的语义更明确

- 问题：Release workflow 发布的是 debug APK，README 已说明测试签名，但 asset 命名和 Release 语义仍容易被误读。
- 证据文件：`.github/workflows/release-android.yml`、`README.md`。
- 风险：用户可能把 debug 包当正式发布包；debuggable、签名和优化预期不一致。
- 最小改动方案：asset 命名改为 `ai-chat-<version>-debug-test.apk`；Release 标题和 notes 强制包含“测试包/debug APK”；正式发布前再引入签名 release 产物。
- 验证命令：手动触发 `Release Android` workflow，检查 asset 名称、notes 和 `apksigner verify --verbose <apk>` 输出。

## 分阶段落地计划

## 本轮执行状态

执行日期：2026-06-07

- 已落地 H1：`ToolExecutor.saveFailure()` 改为显式传入 `sensitiveInputFields`，工具失败、拒绝、取消路径不再引用作用域外 descriptor。
- 已落地 H2：Gateway 默认 `SEARCH_PROVIDER=disabled`，`mock` 只在显式配置时启用；禁用搜索返回 `search_unavailable`。
- 已落地 H3：`ChatViewModel` 退出清理会持久化当前 assistant 为 `Cancelled`；工具确认 pending 时的退出清理也会继续取消 job 并清理 pending 状态。
- 已落地 H4：新增 `DraftHandoffRepository`，超长聊天草稿通过短 ID handoff，route 只保留短草稿或 draftRef。
- 已落地 H5：Provider API Key 改为非 saveable 草稿；Gateway/Search 密钥输入只保存在 Compose 局部 `remember` 草稿中，`ToolsUiState` 只保留 `hasKey/hasToken` 状态；执行时从 SecretStore-backed repository 临时读取，清除密钥改为显式按钮。
- 已落地 H6：Android workflow 监听 `contracts/gateway/**`，协议变更会触发 Android 侧验证。
- 已落地 M2：新增 `search-response.json`、`sandbox-run-response.json`、`gateway-error.json` golden fixture，Android/Go 测试共用契约 fixture。
- 已落地 M3：`agents.md` 中 Room schema 版本更新为 10，并注明以 `AiChatDatabase.kt` 为准。
- 已落地 M1：`ToolExecutor` 迁移到 `tool/runtime`，`domain/tool` 只保留 `ToolExecutionService`、`ToolExecution` 和取消异常等接口/模型；DI 和测试引用同步更新；`docs/基础架构与代码索引.md` 已同步新路径。
- 已落地 M4：抽出 `ChatUiFormatting.kt`、`ChatInputBar.kt`、`ChatToolResultCards.kt`、`ChatMessageBubble.kt`、`ChatStarterPanel.kt`、`ChatControlPanels.kt`、`ToolUiFormatting.kt`、`ToolHistoryInstructions.kt`、`ToolWorkbenchPanels.kt`、`ToolExecutionFormatting.kt`、`ToolHistoryPanel.kt`、`ToolCatalogPanel.kt`、`ToolsUiState.kt`，数据库测试拆为 `AiChatDatabaseTest.kt`、`AiChatMigrationTest.kt`、`AiChatBackupServiceTest.kt`；`ChatScreen.kt` 已降到 665 行，`ToolsScreen.kt` 当前 719 行，`ToolsViewModel.kt` 当前 758 行。
- 已落地 M5：新增 `.github/workflows/android-device.yml`，提供手动设备验证入口，默认跑 `:app:connectedDebugAndroidTest`，可选跑 Baseline Profile instrumentation。
- 已落地 M6：Release workflow debug APK 命名改为 `debug-test`，artifact 和 notes 明确为测试包。

本轮已运行验证：

```powershell
cd gateway
go test ./...
go vet ./...
git diff --check
$env:JAVA_HOME='D:\tmp\temp\codex-jdk17'; $env:ANDROID_HOME='C:\Users\24228\AppData\Local\Android\Sdk'; $env:ANDROID_SDK_ROOT='C:\Users\24228\AppData\Local\Android\Sdk'; .\gradlew.bat :app:compileDebugKotlin --no-daemon --stacktrace
```

结果：`go test ./...`、`go vet ./...`、`git diff --check` 通过；`git diff --check` 仅提示 `ToolsViewModel.kt` 未来会被 Git 从 CRLF 转 LF。

本轮验证阻塞：

- 默认 shell 里 `java.exe` 不在 PATH；已下载临时 Temurin JDK 17 到 `%TEMP%\codex-jdk17`，并用显式 `JAVA_HOME` 复跑。
- 使用临时 JDK 17 但不指定 SDK 时，Android Gradle 失败于 `SDK location not found`；随后用显式 `ANDROID_HOME/ANDROID_SDK_ROOT=C:\Users\24228\AppData\Local\Android\Sdk` 复跑。
- 指定 SDK 后，失败点推进为 SDK 组件缺失：`Failed to find Build Tools revision 35.0.0`。同时 Android SDK manager 访问 `https://dl.google.com/android/repository/addons_list-*.xml` 时 `Remote host terminated the handshake`；`curl.exe -I https://dl.google.com/android/repository/repository2-1.xml` 也失败：`schannel: failed to receive handshake`。
- 2026-06-07 复跑 `.\gradlew.bat :app:testDebugUnitTest --tests "*GatewayClientTest*" --tests "*ToolsViewModelTest*" --tests "*ChatViewModelTest*" --no-daemon --stacktrace`，仍未进入测试阶段：Gradle 配置阶段访问 `dl.google.com/android/repository/addons_list-*.xml` TLS handshake 失败，最终失败于 `Failed to find Build Tools revision 35.0.0`；随后用 `curl.exe --ssl-no-revoke -I` 和 `curl.exe --tlsv1.2 -I` 访问 `repository2-1.xml` 仍是 `schannel: failed to receive handshake`。
- 2026-06-07 在 `ChatScreen`/`ToolsViewModel` 拆分后复跑 `$env:JAVA_HOME='D:\tmp\temp\codex-jdk17'; $env:ANDROID_HOME='C:\Users\24228\AppData\Local\Android\Sdk'; $env:ANDROID_SDK_ROOT='C:\Users\24228\AppData\Local\Android\Sdk'; .\gradlew.bat :app:compileDebugKotlin --no-daemon --stacktrace`，仍未进入 Kotlin 编译阶段：Gradle 配置阶段访问 `dl.google.com/android/repository/addons_list-*.xml` TLS handshake 失败，最终失败于 `Failed to find Build Tools revision 35.0.0`。
- 因此 Android 侧新增/修改的单测已经补齐并尝试运行，但当前本机缺 Android SDK build-tools/platform 组件，且无法通过 `dl.google.com` TLS 下载，必须在 SDK 完整且可访问 Google Maven/SDK 仓库的环境或 GitHub Actions 中复跑。
- `go test -race ./...` 首次失败：`-race requires cgo`；设置 `CGO_ENABLED=1` 后失败：`C compiler "gcc" not found`。
- 已尝试非交互探测测试机环境，但当前 SSH 密码认证返回 `Authentication failed`，未在测试机同步代码、安装依赖或执行 Android 验证。

### 第 1 阶段：先保构建和安全边界

1. 修复 H1，恢复 Android 编译可验证性。
2. 修复 H2，避免 mock 搜索进入真实聊天。
3. 修复 H5，收紧密钥/token 明文生命周期。
4. 验证：Android 编译/单测、Gateway `go test ./...`。

### 第 2 阶段：修用户体验稳定性

1. 修复 H3，取消生成时持久化 `Cancelled`。
2. 修复 H4，大草稿改短 ID handoff。
3. 补对应 ViewModel、Draft、工具结果测试。

### 第 3 阶段：补协议和 CI 闭环

1. 修复 H6，让 contracts 变更触发 Android client 测试。
2. 落地 M2，补 search/sandbox/error fixture。
3. 更新 `contracts/gateway/README.md`，明确当前契约状态。

### 第 4 阶段：低风险整理

1. M4 大文件拆分已按低风险边界落地，后续只建议在新增功能时继续顺手拆分执行流程，不单独做高风险重构。
2. 在 Android SDK 完整环境或 GitHub Actions 中复跑 Android 单测、lint 和 assemble。

## 验证方式

最小总验证：

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lint --no-daemon --stacktrace
cd gateway
go test ./...
go test -race ./...
go vet ./...
```

协议专项验证：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*GatewayClientTest*" --no-daemon --stacktrace
cd gateway
go test ./...
```

设备和发布前验证：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --stacktrace
.\gradlew.bat :baselineprofile:connectedNonMinifiedReleaseAndroidTest --no-daemon --stacktrace
```

当前环境验证缺口：本机已定位到 JDK 17 和 Android SDK，但 Android Gradle 依赖解析访问 `dl.google.com` 时 TLS handshake 失败；需要在 Maven 仓库网络正常的本机、GitHub Actions 或测试机上复跑。
