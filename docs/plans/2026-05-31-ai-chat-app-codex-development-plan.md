# AI 聊天 App Codex 可执行开发文档

日期：2026-05-31
状态：已完成（MVP/P1，P2 延后项见验收报告）
需求来源：`docs/plans/2026-05-31-ai-chat-app-requirements.md`
目标读者：后续执行开发任务的 Codex

## 0. 当前执行进度

- Phase 0 已完成：Android 骨架、Gateway `/health`、基础 CI 和契约目录已创建并验证。
- Phase 1 已完成：Domain 模型、Room schema、DAO、Repository、本地 use case 和 Room in-memory 测试已实现。
- Phase 2 已完成：Android Keystore SecretStore、Provider Config Repository、Provider 设置页、API Key 引用保存、敏感 Header 脱敏、Provider `/models` 连通性测试、删除 Provider 清理密钥和模型偏好已实现。
- Phase 3 已完成：Provider API、OpenAI Responses 请求、Chat Completions fallback、OpenAI-compatible Provider、SSE delta 解析、错误映射和 `SendMessageUseCase` 已实现。
- Phase 4 已完成：聊天页、会话列表入口、消息列表、发送/停止、失败重试、复制消息、编辑重发、清理上下文、会话级 Prompt/模型/参数、临时/敏感会话状态、Prompt 预设管理与应用已实现。
- Phase 5 已完成：基于 `commonmark-java` 和 GFM tables 扩展实现 `MarkdownMessageContent`，支持标题、段落、引用、列表、代码块复制、表格、LaTeX 降级展示、Mermaid 明确降级展示并保留原始代码。
- Phase 6 已完成：图片生成 Provider、图片生成页、本地原图/缩略图保存、历史缩略图列表、清空历史、重新生成、复用 Prompt、分享和保存到系统图片目录已实现。
- Phase 7 已完成：Tool descriptor/registry、Gateway settings、Gateway `/health` 和 `/v1/tools/manifest` client、Gateway manifest 端点、Tools 页面、Network/Execute 权限确认 UI 和 contract fixture 测试已实现。
- Phase 8 已完成：Gateway `/v1/search`、search adapter/mock、Android `web_search` client、Tools 页面搜索输入、权限确认、结果来源链接、结构化错误展示和搜索 `ToolResult` 持久化已实现。
- Phase 9 已完成：Gateway `/v1/sandbox/run`、Docker Python runner、超时与输出截断、`sandbox_unavailable` 结构化错误、Android `code_sandbox` client、Tools 页面代码输入、强制确认、stdout/stderr/exit code/duration 展示和沙箱 `ToolResult` 持久化已实现。
- Phase 10 已完成：Settings 数据管理页、Provider/Prompt/模型偏好/聊天记录 JSON 导入导出、Provider 导出不含 API Key、敏感/临时会话默认不导出、Provider 导入不恢复 API Key、聊天/Provider/Prompt/模型偏好/图片历史/全部数据清理已实现。
- Phase 11 已完成：README 运行说明、MVP 验收报告 `docs/reports/2026-05-31-mvp-acceptance.md`、需求第 6/7/8/11/12/13 节覆盖记录、已知限制和 P2 延后项已整理。
- Phase 3 验证结果：`.\gradlew.bat testDebugUnitTest lint assembleDebug --no-daemon` 通过；`gateway` 下 `go test ./...` 通过。
- Phase 4 验证结果：`.\gradlew.bat testDebugUnitTest lint assembleDebug --no-daemon --stacktrace` 通过；`gateway` 下 `go test ./...` 通过。
- Phase 5 验证结果：`.\gradlew.bat testDebugUnitTest lint assembleDebug --no-daemon --stacktrace` 通过；`gateway` 下 `go test ./...` 通过。
- Phase 6 验证结果：`.\gradlew.bat testDebugUnitTest lint assembleDebug --no-daemon --stacktrace` 通过；`gateway` 下 `go test ./...` 通过。
- Phase 7 验证结果：`.\gradlew.bat testDebugUnitTest lint assembleDebug --no-daemon --stacktrace` 通过；`gateway` 下 `go test ./...` 通过。
- Phase 8 验证结果：`.\gradlew.bat testDebugUnitTest lint assembleDebug --no-daemon --stacktrace` 通过；`gateway` 下 `go test ./...` 通过。
- Phase 9 验证结果：`.\gradlew.bat testDebugUnitTest lint assembleDebug --no-daemon --stacktrace` 通过；`gateway` 下 `go test ./...` 通过。
- Phase 10 验证结果：`.\gradlew.bat testDebugUnitTest lint assembleDebug --no-daemon --stacktrace` 通过；`gateway` 下 `go test ./...` 通过。
- Phase 11 验证结果：`.\gradlew.bat testDebugUnitTest lint assembleDebug --no-daemon --stacktrace` 通过；`gateway` 下 `go test ./...` 通过；`assembleRelease` 通过。模拟器安装和启动成功，raw release 冷启动 `TotalTime` 3646/2558ms，ART `speed` 编译后 release 冷启动 `TotalTime` 1202/969ms，达到 1.5s 目标；baseline/profile 分发优化仍记录为后续项。
- 本地 Windows 中文路径下不再强制 Gradle daemon 使用 `-Dfile.encoding=UTF-8`，避免 Java `@classpath` 文件编码和系统默认编码不一致导致测试 classpath 失效。
- Android 当前使用 AGP 8.13.2、Gradle 8.13、compile/target SDK 36；Robolectric 单测显式运行在 SDK 35 以兼容本地 Java 17。
- Room 当前 schema version 为 4，`1 -> 2` 迁移新增 `conversations.is_sensitive`，`2 -> 3` 迁移补齐图片生成历史字段，`3 -> 4` 迁移将 `tool_invocations.conversation_id` 改为 nullable 以支持无会话工具结果。

## 1. 文档目的

本文档把需求文档转成 Codex 可以逐阶段执行的开发说明。它不是新的产品需求，而是实现路线、工程边界、接口契约、数据模型、测试策略和验收清单。

Codex 执行时必须遵守以下原则：

- 先读需求文档和本文档，再动代码。
- 每次只推进一个明确阶段，阶段结束必须做最小充分验证。
- 基础聊天不能依赖工具网关。
- 工具网关只承载搜索、代码沙箱、MCP 代理等高风险或服务端能力。
- 不实现 P2 功能，除非用户明确升级范围。
- 不把 API Key、完整请求头、完整隐私对话写入日志。
- 不在 Android 本机执行用户代码。
- 不用 WebView 套壳作为主界面。

## 2. 已确认产品方向

主方案采用“原生 Android App + 可选工具网关”。

核心目标：

- Android 原生、轻量、流畅。
- 本地优先保存聊天历史、Provider 配置、Prompt、图片生成历史。
- 支持 OpenAI Provider 与 OpenAI-compatible Provider。
- 支持文本聊天、流式回复、停止生成、失败重试。
- 支持 Markdown、代码块、表格、LaTeX 基础渲染。
- 支持图片生成。
- 通过可选工具网关支持新闻搜索和小规模代码验证。

MVP 必须能在没有工具网关时完成：

- Provider 配置。
- 文本聊天闭环。
- 流式回复、停止生成、失败重试。
- 本地会话和消息历史。
- API Key 加密存储。
- 本地 Prompt。
- 会话基础管理，包括重命名、删除、归档、会话级 Prompt、模型参数、临时会话。
- 图片生成入口与历史。

配置工具网关后应增加：

- 工具清单拉取。
- 新闻/网页搜索工具。
- 代码验证工具。
- 工具权限确认。
- 工具过程和结果展示。

## 3. 默认假设

需求文档未指定的内容按以下默认值执行，除非用户后续明确变更：

- Android 包名：`com.aichat.workbench`。
- App 展示名称：`AI Chat`。
- 语言：Kotlin。
- UI：Jetpack Compose + Material 3。
- Android 架构：单 Android App 模块内按 package 分层，不在第一阶段拆 Gradle 多模块。
- 工具网关：Go，独立目录 `gateway/`。
- 本地数据库：Room。
- 本地设置：DataStore。
- API Key：Android Keystore 托管密钥，加密后只保存在本机。
- 网络：OkHttp + Kotlin Coroutines + Flow。
- JSON：kotlinx.serialization。
- 图片加载与缓存：Coil 或当前维护活跃的 Compose 图片库，执行时核对依赖状态。
- 最小 Android 版本：以当前 Compose/AndroidX 稳定要求为准，默认不低于 API 26。
- OpenAI 官方 Provider：执行时必须核对 OpenAI 官方文档，避免固化过时接口。
- OpenAI-compatible Provider：优先兼容 `/v1/chat/completions` 与常见 SSE 流式格式。
- OpenAI 官方接口如支持服务端保存、metadata 或训练/产品改进相关开关，默认不启用完整隐私内容留存；除非用户明确选择，不主动发送额外 metadata。
- 代码验证 MVP：优先 Python，JavaScript 可作为 P1+ 或后续扩展。
- 搜索 Provider：网关先定义适配器接口，实际搜索服务通过环境变量配置。

官方接口参考：

- OpenAI Responses API：<https://platform.openai.com/docs/api-reference/responses>
- OpenAI Chat Completions API：<https://platform.openai.com/docs/api-reference/chat>
- OpenAI Image Generation：<https://platform.openai.com/docs/guides/image-generation>

### 3.1 需求覆盖矩阵

Codex 执行时必须用本矩阵查漏。若发现某项需求没有阶段任务或验收项，先补本文档再实现。

| 需求来源 | 优先级 | 执行 Phase | 计划落点 |
| --- | --- | --- | --- |
| 6.1 MVP 基础 App、Provider、聊天、本地历史、Prompt、图片 | P0 | Phase 0-6 | 工程骨架、Room、Provider、聊天 UI、Prompt、富文本、图片生成 |
| 7.2 聊天能力：会话管理、编辑重发、系统 Prompt、模型参数、临时会话 | P0/P1 | Phase 1、4、10 | Conversation/Message 模型、ChatScreen、ConversationList、数据清理 |
| 7.3 原生显示：Markdown、代码、表格、LaTeX、Mermaid、图片、思考折叠、引用 | P0/P1 | Phase 5、7、8 | Markdown renderer、Mermaid 静态预览、工具/思考折叠、搜索引用 |
| 7.4 模型与 Provider：OpenAI、OpenAI-compatible、能力、收藏、默认模型、连通性测试 | P0/P1 | Phase 2、3、6 | Provider 设置、OpenAI adapter、compatible adapter、模型偏好 |
| 7.5 图片生成：文生图、参数、历史、缩略图、下载分享 | P0 | Phase 6 | Image adapter、图片页、文件缓存、历史表 |
| 7.6 搜索新闻：结构化来源、确认、失败不编造 | P1 | Phase 7、8 | Tool 权限、Gateway search、ToolResult、引用 UI |
| 7.7 代码验证：沙箱、超时、输出、确认 | P1 | Phase 7、9 | Gateway sandbox、Docker runner、执行结果 UI |
| 7.9 本地数据：加密 Key、Prompt、收藏、导入导出、敏感/临时对话 | P0/P1 | Phase 1、2、10 | SecretStore、prompt_presets、model_preferences、export/import、清理 |
| 8 非功能：性能、轻量、隐私、安全、可维护性 | 全阶段 | Phase 0-11 | 分层规则、安全要求、性能验收、阶段验证 |
| 11-12 MVP 优先级与验收标准 | 验收门槛 | Phase 11 | 发布前总验收和缺口记录 |

## 4. 实现路线对比

### 4.1 路线 A：单 App 模块分层 + 独立 Go 网关，推荐

做法：

- Android 只有一个 `:app` 模块。
- 在 `app/src/main/java/com/aichat/workbench/` 下按 `domain`、`data`、`provider`、`tool`、`feature`、`ui` 分包。
- Go 工具网关独立在 `gateway/`。
- 先完成 P0 闭环，再接 P1 工具。

优点：

- 初始化和 CI 简单。
- 适合当前只有需求文档、没有既有代码的仓库。
- 便于 Codex 连续推进，不被 Gradle 多模块配置拖慢。
- 后续可在代码量增长后拆模块。

代价：

- 包边界依赖开发纪律维护。
- 编译隔离弱于多模块。

结论：作为首轮 MVP 最稳妥。

### 4.2 路线 B：Android 多模块 + 独立 Go 网关

做法：

- 建立 `:core:domain`、`:core:data`、`:core:provider`、`:feature:chat` 等 Gradle 模块。
- Go 网关独立目录。

优点：

- 边界强，测试隔离好。
- 长期适合大型项目。

代价：

- 初始化复杂。
- 依赖配置和 Compose 多模块样板较多。
- MVP 早期容易把时间消耗在工程拆分上。

结论：不作为第一阶段默认方案。P0 稳定后再评估。

### 4.3 路线 C：只做 Android P0，网关后置

做法：

- 完全不创建 `gateway/`。
- 先实现聊天和图片生成。
- P1 时再补工具协议。

优点：

- P0 速度最快。

代价：

- Tool 抽象可能后补，容易返工。
- 搜索与代码验证的 UI 状态、权限确认、结果模型可能重做。

结论：不推荐。可以不实现真实网关逻辑，但应在 P0 就建立 Tool 抽象和 Gateway Client 边界。

## 5. 最终推荐架构

### 5.1 总体结构

```text
用户
  |
Android App
  |-- UI 层：Compose 页面、导航、聊天渲染、工具结果展示
  |-- ViewModel 层：页面状态、事件分发、流式任务生命周期
  |-- Domain 层：会话、消息、Provider、模型能力、工具、Prompt
  |-- Data 层：Room、DataStore、图片缓存、Repository 实现
  |-- Provider 层：OpenAI、OpenAI-compatible、流式解析、图片生成
  |-- Tool 层：工具注册、权限、确认、结果归一化
  |-- Gateway Client：可选工具网关 API 客户端
  |
本地存储
  |-- Room：会话、消息、Prompt、工具调用、图片历史
  |-- Keystore：API Key 加密密钥
  |-- Files/Cache：图片原图、缩略图、导入导出文件
  |
外部服务
  |-- OpenAI 或 OpenAI-compatible Provider
  |-- Optional Gateway
        |-- Search Adapter
        |-- Sandbox Runner
        |-- Future MCP Proxy
```

### 5.2 Android 目录结构

Codex 初始化项目时应创建以下结构。后续可以调整具体文件名，但职责不能混杂。

```text
.
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/aichat/workbench/
│       │   │   ├── MainActivity.kt
│       │   │   ├── app/
│       │   │   │   ├── AiChatApplication.kt
│       │   │   │   ├── AppGraph.kt
│       │   │   │   └── AppDispatchers.kt
│       │   │   ├── navigation/
│       │   │   │   ├── AppDestination.kt
│       │   │   │   └── AppNavHost.kt
│       │   │   ├── domain/
│       │   │   │   ├── model/
│       │   │   │   ├── repository/
│       │   │   │   ├── usecase/
│       │   │   │   └── error/
│       │   │   ├── data/
│       │   │   │   ├── local/
│       │   │   │   ├── settings/
│       │   │   │   ├── crypto/
│       │   │   │   ├── repository/
│       │   │   │   └── mapper/
│       │   │   ├── provider/
│       │   │   │   ├── api/
│       │   │   │   ├── openai/
│       │   │   │   ├── compatible/
│       │   │   │   ├── stream/
│       │   │   │   └── image/
│       │   │   ├── tool/
│       │   │   │   ├── model/
│       │   │   │   ├── registry/
│       │   │   │   ├── gateway/
│       │   │   │   └── permission/
│       │   │   ├── feature/
│       │   │   │   ├── home/
│       │   │   │   ├── chat/
│       │   │   │   ├── provider/
│       │   │   │   ├── prompt/
│       │   │   │   ├── image/
│       │   │   │   ├── tools/
│       │   │   │   └── settings/
│       │   │   └── ui/
│       │   │       ├── theme/
│       │   │       ├── component/
│       │   │       ├── markdown/
│       │   │       └── state/
│       │   └── res/
│       ├── test/
│       └── androidTest/
├── gateway/
│   ├── go.mod
│   ├── cmd/gateway/main.go
│   ├── internal/httpapi/
│   ├── internal/search/
│   ├── internal/sandbox/
│   ├── internal/toolmanifest/
│   ├── internal/config/
│   └── internal/logging/
├── contracts/
│   ├── gateway/
│   │   ├── tool-manifest.schema.json
│   │   ├── search.schema.json
│   │   └── sandbox-run.schema.json
│   └── provider/
├── scripts/test/
├── docs/plans/
└── .github/workflows/
```

### 5.3 分层规则

- `feature/*` 只依赖 ViewModel、Domain use case、UI component。
- `domain/*` 不依赖 Android framework、Room、OkHttp、Compose。
- `provider/*` 负责外部模型 API 和流式解析，不直接写数据库。
- `tool/*` 负责工具模型、权限、Gateway Client，不直接拼 UI。
- `data/*` 负责 Room、DataStore、Keystore、Repository 实现。
- `ui/*` 放纯 UI 组件、主题、Markdown 渲染组件。
- `MainActivity` 只启动 Compose App，不写业务逻辑。

## 6. 核心 Domain 模型

Codex 应先建立 Domain 模型，再落数据库和 UI。模型字段以需求文档为准，允许增加本地实现字段，但不能删除核心语义。

### 6.1 Conversation

```kotlin
data class Conversation(
    val id: ConversationId,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val defaultProviderId: ProviderId?,
    val defaultModel: String?,
    val modelParameters: ModelParameters,
    val systemPrompt: String?,
    val isTemporary: Boolean,
    val archivedAt: Instant?
)
```

约束：

- 会话必须支持创建、重命名、删除、归档。
- 会话级 `systemPrompt` 和 `modelParameters` 覆盖全局默认值，但不改变 Provider 默认配置。
- `isTemporary = true` 的会话默认不写入长期历史。
- 临时会话如需跨页面保存，只能存在内存态或临时表，退出后清理。
- 敏感会话和临时会话默认不进入导出文件。
- 标题可由首条用户消息生成，但不能阻塞发送流程。

### 6.1.1 ModelParameters

```kotlin
data class ModelParameters(
    val temperature: Double?,
    val topP: Double?,
    val maxTokens: Int?
)
```

约束：

- 参数为空时继承 Provider 或 App 默认值。
- 发送请求时只发送用户明确配置或 Provider 明确支持的参数。
- OpenAI 与 OpenAI-compatible 的字段差异由 Provider adapter 处理。

### 6.2 Message

```kotlin
data class Message(
    val id: MessageId,
    val conversationId: ConversationId,
    val role: MessageRole,
    val content: String,
    val contentParts: List<MessagePart>,
    val providerId: ProviderId?,
    val model: String?,
    val status: MessageStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val toolCallId: ToolCallId?,
    val parentMessageId: MessageId?
)
```

枚举：

- `MessageRole`: `System`、`User`、`Assistant`、`Tool`。
- `MessageStatus`: `Draft`、`Pending`、`Streaming`、`Completed`、`Failed`、`Cancelled`。

约束：

- 流式回复期间必须持续更新 assistant message，而不是等完成后一次写入。
- 取消生成后状态为 `Cancelled`，保留已生成文本。
- 失败状态必须保留错误摘要，支持重试。
- 编辑用户消息后重新发送时，不覆盖旧消息和旧回复，应创建新 user message 或版本记录，并用 `parentMessageId` 关联旧链路。
- 工具结果必须使用 `Tool` 或结构化 `ToolResult` 关联，不能伪装为普通 assistant 文本。

### 6.3 ProviderConfig

```kotlin
data class ProviderConfig(
    val id: ProviderId,
    val name: String,
    val type: ProviderType,
    val baseUrl: String,
    val apiKeyRef: String?,
    val headers: Map<String, String>,
    val models: List<ModelConfig>,
    val defaultModel: String?,
    val enabled: Boolean
)
```

约束：

- 数据库中只能保存 `apiKeyRef`，不能保存明文 API Key。
- 自定义 Header 需要按敏感字段脱敏，`Authorization`、`X-API-Key`、`api-key` 等字段不得写日志。
- `baseUrl` 允许 HTTPS。HTTP 只允许用户显式开启，并显示风险提示，主要用于本地服务。

### 6.4 ModelCapability

```kotlin
data class ModelCapability(
    val model: String,
    val text: Boolean,
    val vision: Boolean,
    val imageGeneration: Boolean,
    val toolCalling: Boolean,
    val structuredOutput: Boolean,
    val longContext: Boolean,
    val maxContextTokens: Int?
)
```

约束：

- OpenAI-compatible Provider 默认能力保守。
- 连通性测试只证明服务可用，不等于所有能力都可用。
- 不支持工具调用时，Provider 层必须降级到普通文本模式。
- 模型收藏、默认模型、能力缓存不得写死在 UI 层，应通过 repository 读取。

### 6.5 ToolResult

```kotlin
data class ToolResult(
    val id: ToolCallId,
    val toolName: String,
    val permissionLevel: ToolPermissionLevel,
    val inputSummary: String,
    val output: ToolOutput,
    val status: ToolStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val error: ToolError?
)
```

权限级别：

- `ReadOnly`: 本地只读或纯计算。
- `Network`: 访问外部网络，例如新闻搜索。
- `Execute`: 执行代码。
- `HighRisk`: 文件、账号、持久外部副作用等未来能力。

约束：

- `Network` 及以上默认需要确认。
- `Execute` 必须确认。
- 工具调用 UI 必须展示工具名、权限、输入摘要、状态、结果。

### 6.6 PromptPreset 与 ModelPreference

Prompt 和模型偏好是 P0 本地能力，不能只停留在 UI 占位。

```kotlin
data class PromptPreset(
    val id: PromptPresetId,
    val name: String,
    val description: String?,
    val systemPrompt: String,
    val defaultModel: String?,
    val defaultToolNames: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class ModelPreference(
    val id: ModelPreferenceId,
    val providerId: ProviderId,
    val model: String,
    val isFavorite: Boolean,
    val isDefault: Boolean,
    val capability: ModelCapability?,
    val updatedAt: Instant
)
```

约束：

- Prompt 预设可应用到新会话，也可覆盖当前会话的 system prompt。
- 同一 Provider 下最多一个默认模型。
- 收藏模型只影响选择器排序和默认推荐，不代表能力已验证。

## 7. 本地数据设计

### 7.1 Room 表

MVP 建议表：

- `conversations`
- `messages`
- `provider_configs`
- `prompt_presets`
- `model_preferences`
- `tool_invocations`
- `image_generations`
- `app_settings_snapshot`，可选，用于调试导出，不存密钥

### 7.2 conversations

字段：

- `id TEXT PRIMARY KEY`
- `title TEXT NOT NULL`
- `created_at INTEGER NOT NULL`
- `updated_at INTEGER NOT NULL`
- `default_provider_id TEXT NULL`
- `default_model TEXT NULL`
- `model_parameters_json TEXT NOT NULL`
- `system_prompt TEXT NULL`
- `is_temporary INTEGER NOT NULL DEFAULT 0`
- `archived_at INTEGER NULL`

索引：

- `updated_at DESC`
- `archived_at`

### 7.3 messages

字段：

- `id TEXT PRIMARY KEY`
- `conversation_id TEXT NOT NULL`
- `role TEXT NOT NULL`
- `content TEXT NOT NULL`
- `content_parts_json TEXT NOT NULL`
- `provider_id TEXT NULL`
- `model TEXT NULL`
- `status TEXT NOT NULL`
- `error_summary TEXT NULL`
- `created_at INTEGER NOT NULL`
- `updated_at INTEGER NOT NULL`
- `tool_call_id TEXT NULL`
- `parent_message_id TEXT NULL`

索引：

- `(conversation_id, created_at)`
- `status`
- `tool_call_id`

约束：

- 删除会话时级联删除消息、工具调用和图片记录。
- 重新生成时不要覆盖旧 assistant message，创建新消息并用 `parent_message_id` 关联。
- 编辑重发、清理上下文、分支重试都必须保留可追溯关系，不能直接覆盖历史消息。

### 7.4 provider_configs

字段：

- `id TEXT PRIMARY KEY`
- `name TEXT NOT NULL`
- `type TEXT NOT NULL`
- `base_url TEXT NOT NULL`
- `api_key_ref TEXT NULL`
- `headers_json TEXT NOT NULL`
- `models_json TEXT NOT NULL`
- `default_model TEXT NULL`
- `enabled INTEGER NOT NULL DEFAULT 1`
- `created_at INTEGER NOT NULL`
- `updated_at INTEGER NOT NULL`

约束：

- `headers_json` 写入前必须脱敏或排除敏感 Header。真正敏感 Header 应进入加密存储。
- 删除 Provider 时必须删除对应加密 Key。

### 7.5 prompt_presets

字段：

- `id TEXT PRIMARY KEY`
- `name TEXT NOT NULL`
- `description TEXT NULL`
- `system_prompt TEXT NOT NULL`
- `default_model TEXT NULL`
- `default_tool_names_json TEXT NOT NULL`
- `created_at INTEGER NOT NULL`
- `updated_at INTEGER NOT NULL`

### 7.5.1 model_preferences

字段：

- `id TEXT PRIMARY KEY`
- `provider_id TEXT NOT NULL`
- `model TEXT NOT NULL`
- `is_favorite INTEGER NOT NULL DEFAULT 0`
- `is_default INTEGER NOT NULL DEFAULT 0`
- `capability_json TEXT NULL`
- `created_at INTEGER NOT NULL`
- `updated_at INTEGER NOT NULL`

索引：

- `(provider_id, model)` 唯一。
- `(provider_id, is_default)`。

约束：

- 同一 Provider 下最多一个 `is_default = 1`。
- 删除 Provider 时删除对应模型偏好。
- `capability_json` 只能缓存探测结果或用户确认结果，不得把未验证能力当作事实。

### 7.6 tool_invocations

字段：

- `id TEXT PRIMARY KEY`
- `conversation_id TEXT NOT NULL`
- `message_id TEXT NULL`
- `tool_name TEXT NOT NULL`
- `permission_level TEXT NOT NULL`
- `input_summary TEXT NOT NULL`
- `input_json TEXT NOT NULL`
- `output_json TEXT NULL`
- `status TEXT NOT NULL`
- `error_json TEXT NULL`
- `started_at INTEGER NOT NULL`
- `finished_at INTEGER NULL`

约束：

- `input_json` 不应保存 API Key、完整认证信息。
- 代码执行输入可保存代码摘要和用户确认记录。是否保存完整代码由用户设置决定，默认保存以便追溯当前对话。

### 7.7 image_generations

字段：

- `id TEXT PRIMARY KEY`
- `conversation_id TEXT NULL`
- `prompt TEXT NOT NULL`
- `provider_id TEXT NOT NULL`
- `model TEXT NOT NULL`
- `size TEXT NULL`
- `quality TEXT NULL`
- `count INTEGER NOT NULL`
- `image_uri TEXT NOT NULL`
- `thumbnail_uri TEXT NOT NULL`
- `status TEXT NOT NULL`
- `error_summary TEXT NULL`
- `created_at INTEGER NOT NULL`

约束：

- 历史列表只加载缩略图。
- 原图按需加载。
- 清空数据时必须删除本地图片文件和缩略图。

### 7.8 数据迁移

- Room version 从 `1` 开始。
- 每次 schema 变更必须补 migration 测试。
- 不允许在非开发构建里使用 destructive migration。
- 所有本地实体保留 `created_at`、`updated_at` 或等价字段，方便导入导出和冲突处理。
- 临时会话、敏感会话、密钥引用和图片文件引用在迁移后必须保持原隐私语义，不能因迁移进入普通导出范围。

## 8. API Key 与隐私设计

### 8.1 Key 存储

推荐实现：

- 为每个 Provider 生成 `apiKeyRef = provider:<providerId>:apiKey`。
- 使用 Android Keystore 创建或获取 AES-GCM 主密钥。
- 明文 API Key 只在用户保存时短暂进入内存。
- 加密值存到私有 SharedPreferences 或 DataStore。
- Room 只保存 `apiKeyRef`。

Codex 实现时如果选用 AndroidX Security Crypto，必须确认当前依赖维护状态。若依赖不可用，应实现 Keystore + AES-GCM 的小封装，封装位置为 `data/crypto/SecretStore.kt`。

### 8.2 日志脱敏

必须提供统一脱敏工具：

- `Authorization`
- `Proxy-Authorization`
- `X-API-Key`
- `api-key`
- `OpenAI-Organization`
- `Cookie`
- URL 中的 `key`、`token`、`secret`

规则：

- 日志中敏感值显示为 `<redacted>`。
- 默认不打印完整 prompt、完整聊天记录、完整工具输入。
- Debug 构建可以打印请求 ID、Provider 名、模型名、耗时、状态码。
- Release 构建禁止 verbose 网络日志。

### 8.3 数据清理

设置页必须提供：

- 清空聊天历史。
- 清空图片生成历史。
- 清空 Provider 配置和 API Key。
- 清空所有本地数据。

验收：

- 清空 Provider 后，Room 中配置删除，加密存储中 key 也删除。
- 清空图片历史后，本地原图和缩略图文件删除。

## 9. Provider 层设计

### 9.1 Provider 接口

```kotlin
interface ChatProvider {
    val type: ProviderType

    suspend fun testConnection(config: ProviderConfig): ProviderHealth

    fun streamChat(
        config: ProviderConfig,
        request: ChatRequest
    ): Flow<ProviderStreamEvent>

    suspend fun generateImage(
        config: ProviderConfig,
        request: ImageGenerationRequest
    ): ImageGenerationResponse
}
```

### 9.2 ChatRequest

字段：

- `conversationId`
- `messages`
- `systemPrompt`
- `model`
- `temperature`
- `topP`
- `maxTokens`
- `tools`
- `toolChoice`
- `stream`
- `timeout`

约束：

- P0 工具可以为空。
- 参数为空时不发送，避免 OpenAI-compatible Provider 因未知参数失败。
- `maxTokens` 在 OpenAI Responses API 中可能映射为 `max_output_tokens`，在 Chat Completions 中可能映射为对应兼容字段，映射必须由 adapter 处理。

### 9.3 ProviderStreamEvent

事件类型：

- `Started(requestId)`
- `TextDelta(text)`
- `ReasoningDelta(text)`，如 Provider 支持
- `ToolCallStarted(toolCall)`
- `ToolCallDelta(toolCallId, delta)`
- `ToolCallCompleted(toolCall)`
- `Completed(usage, finishReason)`
- `Failed(error)`

约束：

- UI 不直接解析 Provider 原始 SSE。
- Provider 层必须把原始事件归一化成 `ProviderStreamEvent`。
- 未识别事件不能导致整个流崩溃，应记录脱敏 debug 信息并忽略或转成 warning。

### 9.4 OpenAI Provider

执行时必须先核对官方文档：

- Responses API 当前支持创建 model response、流式 SSE、工具等能力。
- Chat Completions API 仍需支持，用于兼容生态和降级。
- Image Generation 文档用于图片生成请求、响应和尺寸参数。

实现策略：

- Provider 类型 `OpenAI`：文本聊天优先使用 Responses API，保留 Chat Completions fallback。
- Provider 类型 `OpenAICompatible`：默认使用 Chat Completions。
- OpenAI Responses 流式事件和 Chat Completions chunk 必须使用不同 parser，再统一映射为 `ProviderStreamEvent`。
- OpenAI 请求如支持 `store` 或等价服务端留存选项，默认关闭；不得主动发送完整隐私对话到 metadata。
- 图片生成：优先按官方 Image Generation 文档实现；如果官方推荐 Responses 内置图片生成工具，则只在 `ImageProviderAdapter` 内切换实现，UI 和 Domain 不感知具体端点。
- OpenAI Provider 的连通性测试只验证 Key、base URL、默认模型或模型列表可用，不自动发起长文本或图片生成请求。

边界：

- OpenAI 官方 Provider 必须随 P0 Provider 闭环完成，不能只实现 OpenAI-compatible。
- OpenAI-compatible 不承诺支持 Responses API，除非用户显式选择并测试通过。
- 不支持图片生成或工具调用的模型必须通过 `ModelCapability` 禁用对应入口。

### 9.5 OpenAI-compatible Provider

兼容要求：

- `baseUrl` 用户可配。
- API Key 可为空，以支持本地服务。
- 默认路径：
  - Chat：`{baseUrl}/v1/chat/completions`
  - Models：`{baseUrl}/v1/models`，如果用户触发模型拉取
  - Images：`{baseUrl}/v1/images/generations`，仅在能力开启时
- SSE 支持：
  - 解析 `data: {json}` 行。
  - 遇到 `data: [DONE]` 正常结束。
  - 忽略空行和注释行。
  - 单个 chunk 解析失败时返回结构化错误，保留已生成内容。

兼容降级：

- 不支持工具调用时：隐藏或禁用工具调用模式。
- 不支持图片生成时：图片入口提示当前模型不支持。
- 不支持模型列表时：允许用户手动输入模型名。

### 9.6 取消、超时与重试

- 每次发送消息创建独立 coroutine job。
- 用户点击停止时取消 job，并取消底层 OkHttp call。
- 超时分连接超时、读超时、总超时。
- 自动重试只用于连接失败、HTTP 429/5xx 等可重试错误，并且默认不重试流式已开始的请求。
- 用户手动重试必须创建新的 assistant message。

### 9.7 错误归一化

Provider 错误类型：

- `Unauthorized`
- `RateLimited`
- `ModelNotFound`
- `InvalidRequest`
- `NetworkUnavailable`
- `Timeout`
- `ServerError`
- `StreamParseError`
- `UnsupportedCapability`
- `Unknown`

UI 展示：

- 给用户看短错误。
- 详情面板展示状态码、Provider、模型、请求 ID。
- 不展示 API Key、完整 Header、完整隐私 prompt。

## 10. 工具层设计

### 10.1 Tool 接口

```kotlin
interface Tool {
    val name: String
    val displayName: String
    val permissionLevel: ToolPermissionLevel
    val inputSchema: ToolSchema

    suspend fun invoke(input: ToolInput): ToolResult
}
```

MVP 工具：

- `web_search`
- `code_sandbox`
- `image_generation`，可作为 Provider 能力包装，不一定走 Gateway
- `time`，可选本地只读工具
- `calculator`，可选本地只读工具

### 10.2 工具调用生命周期

状态：

- `PendingConfirmation`
- `Running`
- `Succeeded`
- `Failed`
- `Cancelled`

流程：

1. 用户通过入口选择工具，或模型请求工具调用。
2. App 根据权限级别判断是否需要确认。
3. UI 展示工具名、权限、输入摘要。
4. 用户确认后执行。
5. 结果写入 `tool_invocations`。
6. 结果作为消息上下文回写给模型，或直接展示给用户。

### 10.3 权限确认

必须确认：

- 搜索新闻：提示会访问外部网络。
- 代码验证：提示将在远端沙箱执行代码。
- 未来文件上传：提示会读取并发送文件内容。

不需要确认：

- 本地时间。
- 本地计算器。

确认记录：

- 保存确认时间。
- 保存工具名、权限、输入摘要。
- 不保存敏感认证信息。

## 11. 工具网关协议

### 11.1 通用约定

Base URL 用户在 App 设置中配置。网关默认不是基础聊天前置依赖。

Headers：

- `Content-Type: application/json`
- `X-Request-Id: <uuid>`
- `Authorization: Bearer <gateway-token>`，如果用户配置了网关 token

错误响应统一格式：

```json
{
  "error": {
    "code": "sandbox_timeout",
    "message": "Code execution timed out",
    "requestId": "req_123",
    "retryable": false,
    "details": {}
  }
}
```

规则：

- 所有响应包含 request ID。
- 不返回堆栈给 App。
- 日志必须脱敏。
- 默认不保存完整用户输入。

### 11.2 GET /health

响应：

```json
{
  "status": "ok",
  "version": "0.1.0",
  "time": "2026-05-31T00:00:00Z",
  "features": {
    "search": true,
    "sandbox": true,
    "mcp": false
  }
}
```

验收：

- App 设置页可测试连通性。
- 超时或非 2xx 显示结构化错误。

### 11.3 GET /v1/tools/manifest

响应：

```json
{
  "tools": [
    {
      "name": "web_search",
      "displayName": "Web Search",
      "description": "Search recent web or news results",
      "permissionLevel": "network",
      "enabled": true,
      "inputSchema": {
        "type": "object",
        "required": ["query"],
        "properties": {
          "query": {"type": "string"},
          "timeRange": {
            "type": "string",
            "enum": ["day", "week", "month", "any"]
          },
          "limit": {"type": "integer", "minimum": 1, "maximum": 10}
        }
      }
    },
    {
      "name": "code_sandbox",
      "displayName": "Code Sandbox",
      "description": "Run short code in an isolated sandbox",
      "permissionLevel": "execute",
      "enabled": true,
      "inputSchema": {
        "type": "object",
        "required": ["language", "code"],
        "properties": {
          "language": {"type": "string", "enum": ["python"]},
          "code": {"type": "string"},
          "stdin": {"type": "string"},
          "timeoutMs": {"type": "integer", "minimum": 100, "maximum": 5000}
        }
      }
    }
  ]
}
```

### 11.4 POST /v1/search

请求：

```json
{
  "query": "OpenAI latest image generation API",
  "timeRange": "week",
  "limit": 5,
  "locale": "zh-CN"
}
```

响应：

```json
{
  "requestId": "req_123",
  "query": "OpenAI latest image generation API",
  "searchedAt": "2026-05-31T00:00:00Z",
  "results": [
    {
      "title": "Result title",
      "summary": "Short summary",
      "url": "https://example.com/article",
      "source": "example.com",
      "publishedAt": "2026-05-30T12:00:00Z",
      "fetchedAt": "2026-05-31T00:00:00Z"
    }
  ]
}
```

约束：

- `title`、`url`、`source` 必须有。
- `publishedAt` 不确定时可为空，但 `fetchedAt` 必须有。
- 搜索失败时返回结构化错误，App 不应让模型编造搜索结果。
- App 展示回答时必须展示引用来源链接。

### 11.5 POST /v1/sandbox/run

请求：

```json
{
  "language": "python",
  "code": "print(1 + 1)",
  "stdin": "",
  "timeoutMs": 3000
}
```

响应：

```json
{
  "requestId": "req_123",
  "language": "python",
  "status": "completed",
  "exitCode": 0,
  "stdout": "2\n",
  "stderr": "",
  "durationMs": 81,
  "timedOut": false,
  "truncated": false
}
```

失败示例：

```json
{
  "requestId": "req_123",
  "language": "python",
  "status": "failed",
  "exitCode": 1,
  "stdout": "",
  "stderr": "NameError: name 'x' is not defined\n",
  "durationMs": 44,
  "timedOut": false,
  "truncated": false
}
```

约束：

- MVP 只承诺 Python。
- 必须有超时，默认 3000ms，最大 5000ms。
- stdout + stderr 总输出限制默认 64KB。
- 不允许无限网络访问。
- 不允许裸跑宿主机命令。

## 12. Go 工具网关设计

### 12.1 技术边界

- 使用 Go 标准库 `net/http` 起步。
- 配置通过环境变量和可选配置文件。
- 日志使用结构化日志，敏感字段脱敏。
- 所有 handler 必须接收 context，遵守请求超时。
- 不保存默认聊天历史。
- 不代理普通模型聊天。

### 12.2 gateway 目录职责

```text
gateway/
├── cmd/gateway/main.go              # 启动、读取配置、注册路由
├── internal/config/                 # 环境变量解析、默认值
├── internal/httpapi/                # handler、request/response DTO、错误映射
├── internal/search/                 # SearchService 接口与 adapter
├── internal/sandbox/                # SandboxRunner 接口与 Docker runner
├── internal/toolmanifest/           # 工具清单
└── internal/logging/                # 脱敏日志
```

### 12.3 搜索服务

接口：

```go
type SearchService interface {
    Search(ctx context.Context, req SearchRequest) (SearchResponse, error)
}
```

实现策略：

- `SEARCH_PROVIDER=disabled`：返回 `search_unconfigured`。
- `SEARCH_PROVIDER=mock`：只用于测试，不进入 release 默认配置。
- `SEARCH_PROVIDER=brave|bing|serpapi`：具体 Provider 由后续配置 Key 决定。

要求：

- 真实搜索 adapter 不得把服务 Key 返回给 App。
- 失败时保留 request ID。
- 搜索结果必须保留来源 URL。

### 12.4 沙箱服务

接口：

```go
type SandboxRunner interface {
    Run(ctx context.Context, req RunRequest) (RunResponse, error)
}
```

推荐 Docker runner：

- 使用 `exec.CommandContext` 调用容器运行时。
- 禁止拼接 shell 字符串执行。
- 将代码写入临时目录，目录执行后删除。
- 容器参数包括：
  - `--rm`
  - `--network none`
  - `--memory 128m`
  - `--cpus 0.5`
  - `--pids-limit 64`
  - `--read-only`
  - `--tmpfs /tmp:rw,noexec,nosuid,size=16m`
- 超时由 Go context 和容器参数双重控制。

如果 Docker 不可用：

- 不允许 fallback 到宿主机裸执行。
- 返回 `sandbox_unavailable`。

安全说明：

- Docker 隔离不是完整安全边界。若要公网开放，需要二期加 gVisor、Firecracker 或独立隔离执行环境。
- MVP 可用于个人或可信环境，不应默认暴露到公网。

## 13. UI 与交互设计

### 13.1 导航结构

底层页面：

- `HomeScreen`
- `ChatScreen`
- `ConversationListScreen`
- `ProviderSettingsScreen`
- `PromptPresetScreen`
- `ImageGenerationScreen`
- `ToolSettingsScreen`
- `AppSettingsScreen`

移动端导航建议：

- 首页显示生产力入口和最近对话。
- 聊天页顶部显示当前模型和 Provider。
- 对话列表从导航抽屉或单独页面进入。
- 设置页集中管理 Provider、工具网关、隐私与数据。

### 13.2 首次启动

流程：

1. 检查是否有启用的 Provider。
2. 没有 Provider 时进入 Provider 配置引导。
3. 用户填写 Base URL、API Key、模型名。
4. 用户可点击连通性测试。
5. 保存后进入首页或直接进入新对话。

验收：

- 用户能在 1 分钟内完成 OpenAI-compatible 配置并发送第一条消息。
- 未配置 Provider 时，发送按钮不可用并引导配置。

### 13.3 首页

内容：

- 普通聊天。
- 写作润色。
- 代码验证。
- 新闻搜索。
- 图片生成。
- 文档总结，P2 前不作为可用主入口。
- 最近对话。

实现规则：

- 这些入口本质是 Prompt、默认模型和工具组合。
- 不做成割裂的独立产品。
- 点击“新闻搜索”时创建带 `web_search` 工具上下文的新会话。
- 点击“代码验证”时创建带 `code_sandbox` 工具上下文的新会话。

### 13.4 聊天页

核心组件：

- 顶部栏：返回、标题、模型切换、会话级 Prompt/参数入口、更多菜单。
- 消息列表：LazyColumn，长内容稳定滚动。
- 消息气泡：用户、助手、工具结果、模型思考内容分样式。
- 工具过程卡片：可折叠，显示状态和结果。
- 模型思考卡片：默认折叠，用户可展开查看。
- 输入区：多行输入、发送、停止、重试入口。

状态：

- 空会话。
- 正在发送。
- 正在流式生成。
- 正在取消。
- 失败可重试。
- 工具待确认。
- 工具执行中。
- 临时会话。
- 敏感会话。

交互：

- 创建、重命名、删除、归档对话必须在对话列表或聊天页更多菜单中可达。
- 会话级 system prompt、temperature、top_p、max_tokens 可编辑，并只影响当前会话。
- 支持按会话切换 Provider 和模型，默认继承全局默认 Provider/模型。
- 编辑用户消息后重新发送，旧 assistant 回复不覆盖。
- 停止生成保留已生成内容。
- 复制消息。
- 重试失败消息。
- 清理上下文后继续同一会话。
- 临时会话退出后默认不入库；敏感会话默认不导出。
- 对话内搜索可以 P1 后段实现，P0 预留入口即可。

验收：

- 新建、重命名、删除、归档会话都能反映到本地列表。
- 编辑重发后能看到新回复，旧消息链仍可追溯。
- 切换会话模型不会改变 Provider 全局默认模型。
- 思考内容和工具过程默认折叠，不挤占普通聊天阅读空间。

### 13.5 Markdown 与富文本

MVP 必须支持：

- Markdown 段落。
- 列表。
- 代码块。
- 行内代码。
- 表格。
- LaTeX 基础显示。
- Mermaid 静态预览或导出图片预览。
- 图片消息。

降级规则：

- 渲染失败保留原始文本。
- Mermaid MVP 先做静态预览：优先接入维护活跃的 Android/Compose 渲染方案；若无稳定依赖，可使用隔离 WebView 只渲染单个 Mermaid 图，不作为 App 主界面。
- 外部网络 Mermaid 渲染默认禁用。预览失败时显示原始 Mermaid 代码块、错误摘要和复制入口，不得让消息不可读。
- 超长代码块默认折叠，避免卡顿。

实现建议：

- 第一阶段可用维护活跃的 Compose Markdown 渲染库。
- 若库对表格或 LaTeX 支持不足，补局部组件，不重写完整 Markdown 引擎。
- 代码高亮可先按语言标签做基础样式，复杂高亮后置。
- Mermaid 高质量原生预览属于 P2；MVP 只要求静态可核对、失败可读。

### 13.6 图片生成页

字段：

- Prompt。
- Provider。
- Model。
- Size。
- Quality。
- Count。
- Style 或等价 Provider 参数。
- Generate。

结果：

- 缩略图网格。
- 原图预览。
- 下载。
- 分享。
- 重新生成。
- 复用 Prompt。

约束：

- 图片生成历史写入 Room。
- 原图和缩略图写入 App 私有文件目录。
- 生成中显示进度状态。
- 失败显示错误摘要和重试。

## 14. 状态管理

### 14.1 ViewModel 模式

每个页面使用：

- `UiState`
- `UiEvent`
- `UiEffect`

示例：

```kotlin
data class ChatUiState(
    val conversationId: ConversationId?,
    val title: String,
    val messages: List<MessageUiModel>,
    val input: String,
    val selectedProvider: ProviderSummary?,
    val selectedModel: String?,
    val modelParameters: ModelParametersUiModel,
    val systemPrompt: String?,
    val isTemporary: Boolean,
    val isSensitive: Boolean,
    val isGenerating: Boolean,
    val pendingToolConfirmation: ToolConfirmationUiModel?,
    val error: UiError?
)
```

规则：

- ViewModel 暴露 `StateFlow<ChatUiState>`。
- 一次性事件用 `Channel` 或 `SharedFlow`。
- 流式生成的 Job 由 ViewModel 管理，`onCleared` 必须取消。
- Repository 不持有 UI state。

### 14.2 发送消息流程

1. ViewModel 校验输入和 Provider。
2. 创建或获取 Conversation。
3. 合并会话级 system prompt、模型参数和工具上下文。
4. 写入 user message。
5. 创建 assistant message，状态 `Pending`。
6. 调用 use case `SendMessageUseCase`。
7. Provider 返回 `TextDelta` 或 `ReasoningDelta` 时增量更新 assistant message。
8. 完成时状态改为 `Completed`。
9. 失败时状态改为 `Failed` 并写入错误摘要。

### 14.2.1 编辑重发流程

1. 用户编辑历史 user message。
2. ViewModel 生成新的 user message 或 message revision。
3. 新 assistant message 通过 `parentMessageId` 关联旧回复链。
4. 重新调用 `SendMessageUseCase`，旧回复保留只读。
5. UI 展示当前链路，允许用户回看旧链路。

### 14.3 停止生成流程

1. 用户点击停止。
2. ViewModel 设置 UI 状态为取消中。
3. 取消当前 generation job。
4. Provider 层取消网络 call。
5. assistant message 状态改为 `Cancelled`。
6. 输入区恢复可操作。

## 15. 图片缓存与文件设计

目录建议：

```text
files/
└── images/
    ├── originals/
    └── thumbnails/
cache/
└── markdown/
```

规则：

- 原图文件名使用 image generation id。
- 缩略图尺寸按列表展示需要生成，避免加载原图。
- 分享图片时通过 FileProvider。
- 清空历史必须删除文件。
- 导出聊天记录时默认只导出图片元数据和可选文件引用，不默认打包所有原图。

## 16. 导入导出

P1 应完成基础导入导出配置：

导出内容：

- Provider 配置，不含 API Key。
- Prompt 预设。
- 模型收藏、默认模型和能力缓存。
- App 设置。
- 用户选择包含时导出聊天记录。

不默认导出：

- API Key。
- 原始图片文件。
- 临时对话。
- 敏感对话。
- 完整工具认证信息。

格式：

```json
{
  "version": 1,
  "exportedAt": "2026-05-31T00:00:00Z",
  "providers": [],
  "prompts": [],
  "modelPreferences": [],
  "settings": {},
  "conversations": []
}
```

导入规则：

- 先验证版本。
- 冲突时创建新 id，不覆盖现有数据，除非用户选择覆盖。
- 不导入未知敏感字段。
- 导入聊天记录时只导入消息内容、工具结果摘要和图片元数据；原图文件需要用户单独选择导入包。
- 导入 Provider 时不创建 API Key，必须提示用户重新填写密钥。

## 17. 性能要求与实现点

必须满足：

- 冷启动进入主界面小于 1.5 秒。
- 会话列表本地秒开。
- 长回复流式展示不卡输入区。
- 取消生成后网络请求停止。
- 图片历史不加载原图。

实现点：

- 启动时不做 Provider 连通性测试。
- 启动时不加载全部消息内容，只加载最近会话摘要。
- 聊天页分页加载消息。
- 长 Markdown 渲染放在可取消的后台计算或增量组件中。
- 图片列表只读缩略图。
- 数据库查询建立必要索引。
- 网络请求和图片加载不在主线程。

## 18. 安全要求

禁止：

- Android 本机执行用户代码。
- 网关不可用时 fallback 到 App 本机执行代码。
- 普通日志打印 API Key。
- 普通日志打印完整私密对话。
- 搜索失败后让模型编造搜索结果。
- Provider UI 层直接拼接 HTTP 请求。
- 数据库保存明文 API Key。
- 默认把完整隐私对话写入 Provider metadata、工具网关日志或导出文件。

必须：

- 工具调用前按权限确认。
- 搜索结果展示来源链接。
- 代码执行展示 stdout、stderr、exit code、duration。
- 网关代码执行有超时和输出限制。
- HTTP 明文 Base URL 需要用户显式允许。
- OpenAI 官方接口实现前重新核对 Responses、Chat Completions、Image Generation 文档。
- Release 构建关闭敏感 debug 日志。

## 19. 测试策略

### 19.1 Android 单元测试

覆盖：

- Domain model mapper。
- Provider 请求构建。
- OpenAI Responses SSE 与 Chat Completions SSE 解析。
- Provider 错误归一化。
- Tool 权限判断。
- Chat 发送/停止/失败重试 use case。
- 编辑重发、会话级 Prompt、模型参数合并 use case。
- SecretStore 不返回明文到日志。
- PromptPreset 和 ModelPreference repository。
- Repository 写入和读取。

推荐工具：

- JUnit。
- kotlinx-coroutines-test。
- OkHttp MockWebServer。
- Room in-memory DB。

### 19.2 Android UI 测试

覆盖主路径：

- 首次启动无 Provider 时显示配置入口。
- 添加 Provider 后能进入聊天页。
- 发送消息后消息列表出现 user 和 assistant 占位。
- 流式 delta 更新消息内容。
- 点击停止后状态变为 Cancelled。
- 编辑消息后重新发送不覆盖旧回复。
- 会话级模型切换、Prompt、参数设置只影响当前会话。
- 工具过程和模型思考内容默认折叠。
- 图片生成历史显示缩略图。

### 19.3 Gateway 测试

覆盖：

- `/health`。
- `/v1/tools/manifest`。
- `/v1/search` 参数校验和错误结构。
- `/v1/sandbox/run` 正常执行、语法错误、超时、输出截断。
- Docker 不可用时返回 `sandbox_unavailable`。
- 日志脱敏函数。

### 19.4 契约测试

`contracts/` 下维护 JSON schema 或 golden fixtures：

- tool manifest。
- search request/response。
- sandbox request/response。
- gateway error。

Android Gateway Client 和 Go Gateway 都要用同一批 fixture 测试，避免协议漂移。

### 19.5 验证命令

Windows 本地：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lint
.\gradlew.bat assembleDebug
cd gateway
go test ./...
```

如果配置了 lint 工具：

```powershell
cd gateway
golangci-lint run --fix ./...
```

如果加入 CI：

- Android：单元测试、lint、assembleDebug。
- Gateway：`go test ./...`。
- 契约测试：Android 与 Go 都读取 `contracts/` fixture。

## 20. GitHub Actions 建议

创建：

- `.github/workflows/android.yml`
- `.github/workflows/gateway.yml`

Android workflow：

- checkout。
- setup Java。
- setup Gradle。
- run `./gradlew testDebugUnitTest lint assembleDebug`。

Gateway workflow：

- checkout。
- setup Go。
- run `go test ./...`。

说明：

- 当前目录不是 git 仓库，Codex 初始化项目时如果用户要求版本管理，再执行 `git init`。
- 没有用户要求时，不主动提交 commit。

## 21. 阶段执行计划

### Phase 0：工程初始化

目标：

- 创建 Android 原生项目骨架。
- 创建 Go Gateway 骨架。
- 建立基础 CI 和测试命令。

主要任务：

- 创建 `settings.gradle.kts`、根 `build.gradle.kts`、`app/build.gradle.kts`。
- 创建 `MainActivity` 和最小 Compose 入口。
- 建立包结构。
- 创建 `gateway/go.mod`、`cmd/gateway/main.go`。
- 创建 `/health` handler。
- 创建 `contracts/gateway` 目录。
- 创建 `scripts/test/`。

验收：

- Android 能 assemble debug。
- Gateway 能启动并返回 `/health`。
- `go test ./...` 通过。

### Phase 1：Domain 与本地数据骨架

目标：

- 建立核心模型、Room schema、Repository 接口。

主要任务：

- 实现 `Conversation`、`Message`、`ProviderConfig`、`ModelCapability`、`ToolResult`、`PromptPreset`、`ModelPreference`、`ModelParameters`。
- 实现 Room entities、DAO、Database。
- 实现 Repository interface 和本地实现。
- 实现会话创建、重命名、删除、归档、临时会话标记的基础 use case。
- 实现 Prompt 预设和模型收藏/默认模型的本地读写。
- 实现基础 migration 测试。
- 实现 `AppDispatchers`。

验收：

- 能创建会话、写入消息、读取会话列表。
- 能重命名、归档、删除会话。
- 能保存 Prompt 预设和模型收藏。
- Room in-memory 测试通过。
- Domain 不依赖 Android UI 或网络。

### Phase 2：API Key 加密与 Provider 配置

目标：

- 用户能配置 Provider，API Key 加密保存。

主要任务：

- 实现 `SecretStore`。
- 实现 Provider 配置 Repository。
- 实现 Provider 设置页。
- 支持 OpenAI 与 OpenAI-compatible Provider 类型。
- 支持 Base URL、API Key、模型名、自定义 Header。
- 支持默认 Provider 和默认模型。
- 支持模型收藏、手动模型名、能力缓存占位。
- 支持连通性测试入口。

验收：

- Room 中不出现明文 API Key。
- 删除 Provider 会删除对应 secret。
- 配置页保存后重启仍可读取 Provider。
- 同一 Provider 下默认模型唯一。

### Phase 3：Provider Chat 闭环

目标：

- 支持 OpenAI 与 OpenAI-compatible 文本聊天和流式回复。

主要任务：

- 实现 `ChatProvider` 接口。
- 实现 OpenAI Responses request builder、stream parser 和 Chat Completions fallback。
- 实现 OpenAI-compatible request builder。
- 实现 SSE parser。
- 实现错误归一化。
- 实现 `SendMessageUseCase`。
- 合并会话级 system prompt、模型参数和工具上下文。
- OpenAI 官方请求默认不发送完整隐私内容到 metadata，支持服务端留存开关时默认关闭。
- 用 MockWebServer 测试普通响应、流式响应、错误响应。

验收：

- Mock Provider 下能完整发送消息并流式更新。
- OpenAI Responses mock event 和 Chat Completions mock chunk 都能解析为 `ProviderStreamEvent`。
- 解析 `[DONE]` 正常结束。
- 401、429、5xx 能映射为结构化错误。
- 会话级参数能映射到对应 Provider 请求字段。

### Phase 4：聊天 UI

目标：

- 用户能创建对话、发送消息、停止生成、失败重试。

主要任务：

- 实现 `HomeScreen`。
- 实现 `ConversationListScreen`。
- 实现 `ChatScreen`。
- 实现消息列表、输入区、发送/停止按钮。
- 实现 ViewModel 状态机。
- 实现创建、重命名、删除、归档对话。
- 实现会话级 Prompt、模型切换、temperature、top_p、max_tokens 设置入口。
- 实现临时会话和敏感会话 UI 状态。
- 实现复制消息、编辑重发、重试失败消息、清理上下文。
- 实现 PromptPresetScreen 的创建、编辑、应用。
- 实现工具过程和模型思考内容的默认折叠展示。

验收：

- 无 Provider 时引导配置。
- 有 Provider 时可以发送消息。
- 流式生成时输入区不阻塞。
- 停止生成后状态正确。
- 失败后可重试。
- Prompt 预设能应用到新会话。
- 切换当前会话模型不影响全局默认模型。
- 临时会话退出后不进入长期历史。
- 编辑重发不覆盖旧消息链。

### Phase 5：Markdown、代码块、表格、LaTeX 基础渲染

目标：

- Assistant 消息具备基本可读的富文本展示。

主要任务：

- 选择并接入 Compose Markdown 渲染方案。
- 封装 `MarkdownMessageContent`。
- 支持代码块复制。
- 支持表格基础展示。
- 支持 LaTeX 基础展示或降级。
- 支持 Mermaid 静态预览或导出图片预览；预览失败时保留原始代码块。
- 超长内容折叠或懒加载。

验收：

- Markdown 渲染失败时显示原始文本。
- 长代码块不会明显卡顿。
- 表格可读。
- Mermaid 代码块能看到静态预览或明确的预览失败状态，原文仍可读可复制。
- 代码块可复制。

### Phase 6：图片生成

目标：

- 支持文生图、保存历史、缩略图缓存。

主要任务：

- 实现 `ImageGenerationRequest` 和 Provider adapter。
- 实现图片生成页。
- 实现原图保存和缩略图生成。
- 实现图片历史列表。
- 支持 size、quality、count、style 或等价 Provider 参数。
- 实现下载、分享、重新生成、复用 Prompt。

验收：

- Mock 图片接口下能生成并保存历史。
- 历史列表加载缩略图。
- 清空图片历史删除文件。
- 不支持图片能力的模型显示明确提示。
- 图片生成不要求经过 Gateway。

### Phase 7：Tool 抽象与 Gateway Client

目标：

- 建立工具注册、权限确认和网关协议客户端。

主要任务：

- 实现 Tool model 和 registry。
- 实现 Gateway settings。
- 实现 `/health` 和 `/v1/tools/manifest` client。
- 实现工具权限确认 UI。
- 实现工具调用过程折叠展示。
- 用 contracts fixture 测试协议解析。

验收：

- 未配置 Gateway 时基础聊天不受影响。
- 配置 Gateway 后能拉取 manifest。
- Network/Execute 工具调用前出现确认。

### Phase 8：新闻搜索工具

目标：

- App 可通过网关搜索新闻/网页，并展示来源引用。

主要任务：

- Gateway 实现 search interface、mock adapter、真实 adapter 占位。
- App 实现 `web_search` tool。
- 首页新闻搜索入口绑定工具。
- 搜索结果作为 ToolResult 写入数据库。
- 模型回答时带结构化来源。
- UI 展示引用链接和时间。
- 搜索事实来源与模型回答分开展示。

验收：

- 搜索成功显示标题、摘要、链接、时间。
- 搜索失败显示结构化错误。
- 模型不会在无搜索结果时编造来源。
- 用户能点开原始来源链接。

### Phase 9：代码验证工具

目标：

- 通过网关沙箱执行短 Python 代码。

主要任务：

- Gateway 实现 `/v1/sandbox/run`。
- Docker runner 加资源限制。
- 输出截断和超时处理。
- App 实现 `code_sandbox` tool。
- UI 展示 stdout、stderr、exit code、duration。
- 工具调用前强制确认。

验收：

- `print(1 + 1)` 返回 stdout。
- 语法错误返回 stderr 和非零 exit code。
- 超时代码返回 timeout。
- Docker 不可用时返回 `sandbox_unavailable`。
- App 不在本机执行代码。

### Phase 10：导入导出、清理、隐私收尾

目标：

- 完成基础数据管理和隐私验收。

主要任务：

- 实现导出 Provider 配置，不含 API Key。
- 实现导出/导入 Prompt。
- 实现导出/导入模型收藏和默认模型。
- 实现用户选择后的聊天记录导出/导入。
- 实现清空聊天历史。
- 实现清空 Provider 和 API Key。
- 实现清空 Prompt、模型偏好和图片历史。
- 实现清空所有数据。
- 检查日志脱敏。

验收：

- 导出文件没有 API Key。
- 默认不导出临时会话、敏感会话和原始图片文件。
- 导入 Provider 后需要用户重新填写 API Key。
- 清空数据后数据库和图片文件清理完成。
- 日志检查不出现敏感字段。

### Phase 11：性能与发布前验收

目标：

- 对照需求文档完成 MVP 验收。

主要任务：

- 冷启动粗测。
- 长回复流式渲染测试。
- 长会话滚动测试。
- 图片历史列表测试。
- 取消网络请求测试。
- 需求覆盖矩阵逐项核对。
- 整理 README 或运行说明。

验收：

- P0 全部通过。
- P1 核心工具通过。
- 需求文档第 6、7、8、11、12、13 节均有通过、失败或 P2 延后记录。
- 未完成项目明确记录为 P2 或已知限制。

## 22. Codex 执行检查清单

每个阶段开始前：

- 读取本文档和需求文档。
- 用 `rg --files` 查看当前文件。
- 确认是否已有用户改动。
- 不覆盖不相关改动。

每个阶段开发中：

- 只改本阶段相关文件。
- 不引入未请求功能。
- 不为单次使用创建复杂抽象。
- 所有错误带上下文。
- 不写敏感日志。

每个阶段结束前：

- 运行阶段最小验证命令。
- 修复能修的失败。
- 记录未能验证的原因。
- 对照阶段验收项给出通过/失败判断。

## 23. 交付标准

MVP P0 完成标准：

- App 能配置 OpenAI Provider。
- App 能配置 OpenAI-compatible Provider。
- App 能完成文本聊天。
- App 支持流式显示和停止生成。
- App 支持失败重试。
- App 本地保存会话和消息。
- App 支持会话重命名、删除、归档、临时会话。
- App 支持本地 Prompt 预设，并能应用到会话。
- App 支持会话级模型、system prompt 和模型参数。
- API Key 加密保存。
- Markdown、代码块、表格、LaTeX、Mermaid 降级显示基本可读。
- App 支持图片生成和历史。

P1 首版完成标准：

- App 可配置工具网关。
- Gateway 可返回工具清单。
- 新闻搜索工具可用并展示来源。
- 代码验证工具可用并展示 stdout、stderr、exit code、duration。
- 工具调用有权限确认。
- 导入导出配置、Prompt、模型偏好和用户选择的聊天记录可用，默认不导出 API Key。
- 对话内搜索可用或明确记录为 P1 后段未完成项。

安全完成标准：

- 普通日志无 API Key。
- 数据库无明文 API Key。
- App 不执行用户代码。
- 搜索失败不编造来源。
- 代码沙箱有超时、输出限制、资源限制。
- 临时会话和敏感会话默认不导出。

## 24. 不做清单

首轮不要做：

- 企业后台。
- 多租户。
- 团队权限。
- 复杂 Agent 工作流编排。
- 完整 IDE。
- Android 本机代码执行。
- 自研大模型推理。
- WebView 主界面。
- MCP 完整接入。
- 文件上传和文档总结。
- 长对话自动压缩。
- 对话分支全功能。

## 25. 推荐给 Codex 的执行提示词

后续可以把下面这段作为 Codex 的执行入口：

```text
请阅读 docs/plans/2026-05-31-ai-chat-app-requirements.md 和 docs/plans/2026-05-31-ai-chat-app-codex-development-plan.md。
按开发文档从 Phase 0 开始实现。每次只推进一个阶段。
阶段开始前先检查现有文件和未提交改动，阶段结束后运行该阶段最小验证命令。
每个阶段结束前对照“需求覆盖矩阵”和本阶段验收项查漏。
不要实现 P2 功能，不要让基础聊天依赖工具网关，不要在 Android 本机执行用户代码，不要记录 API Key 或完整隐私对话。
如果遇到需求冲突，先指出冲突和推荐取舍，再继续最小正确实现。
```

## 26. 当前已知风险

### OpenAI 与 OpenAI-compatible 差异

风险：

- OpenAI 官方接口和 OpenAI-compatible 生态的端点、字段、流式事件不完全一致。

对策：

- Provider adapter 分开。
- OpenAI Provider 执行时核对官方文档。
- Compatible Provider 参数保守发送。
- 能力不明确时降级。

### Markdown 渲染复杂

风险：

- Compose 下 Markdown、表格、LaTeX、Mermaid 全量支持成本高。

对策：

- MVP 先保证可读和稳定。
- Mermaid 可降级。
- 渲染失败显示原文。

### 代码沙箱安全

风险：

- Docker 不是绝对安全边界。

对策：

- MVP 不默认公网开放。
- 无 Docker 不执行。
- 二期再引入更强隔离。

### 工具网关复杂化

风险：

- 网关容易膨胀成代理所有请求的重后端。

对策：

- 明确不代理普通聊天。
- 明确不保存聊天历史。
- 只做搜索、沙箱、未来 MCP 代理。

## 27. 最小里程碑

Milestone 1：可运行骨架

- Android 空壳可启动。
- Gateway `/health` 可用。
- CI 或本地验证命令可跑。

Milestone 2：本地数据和 Provider 配置

- Provider 配置可保存。
- API Key 加密。
- 会话和消息可写入。

Milestone 3：文本聊天闭环

- OpenAI-compatible Mock 流式聊天可用。
- 真实 Provider 可手动配置验证。

Milestone 4：可用聊天产品

- 首页、聊天页、历史记录、Markdown 基础渲染完成。

Milestone 5：图片生成

- 文生图和图片历史完成。

Milestone 6：工具网关

- 工具清单、搜索、沙箱完成。

Milestone 7：首版验收

- P0 和 P1 核心验收通过。
- 已知限制记录清楚。
