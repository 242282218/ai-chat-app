# Phase 4: 清理和优化

**预估时间**: 2-3 天  
**难度**: ⭐⭐⭐☆☆  
**风险**: 低

## 目标

- 删除旧代码和备份文件
- 性能优化
- 代码质量提升
- 文档更新
- 准备合并到 main

## 步骤 1: 代码清理

### 1.1 删除备份文件

```bash
# 删除所有 .backup 文件
find app/src/main/java/com/aichat/workbench/feature/chat -name "*.backup" -delete

# 具体文件（如果上面的命令不放心，可以逐个删除）
rm app/src/main/java/com/aichat/workbench/feature/chat/ChatMessageList.kt.backup
rm app/src/main/java/com/aichat/workbench/feature/chat/ChatInputBar.kt.backup
rm app/src/main/java/com/aichat/workbench/feature/chat/ChatTopBar.kt.backup
```

### 1.2 清理注释代码

打开 `ChatScreen.kt`，删除所有旧代码的注释：

```kotlin
// 删除这类注释块:
/*
ChatMessageList(
    messages = state.messages,
    ...
)
*/
```

**原则**: 如果代码已完全被 Stream 组件替换且运行正常，删除注释

### 1.3 删除未使用的文件

根据 `docs/implementation-guide/deprecated-files.md`，删除未使用的文件：

```bash
# 示例（根据实际情况调整）
rm app/src/main/java/com/aichat/workbench/feature/chat/ChatMessageList.kt
rm app/src/main/java/com/aichat/workbench/feature/chat/ChatInputBar.kt
# 注意: ChatTopBar 可能还在使用自定义部分，谨慎删除
```

**验证步骤**:
1. 删除文件
2. 重新编译: `./gradlew clean assembleDebug`
3. 如果编译失败，说明文件还在使用，恢复后再评估

### 1.4 清理未使用的导入

使用 Android Studio 的自动清理功能：

```
Code -> Optimize Imports (Ctrl+Alt+O / Cmd+Option+O)
```

对所有修改过的文件执行一次。

### 1.5 提交清理

```bash
git add .
git commit -m "refactor(stream): 步骤 1 - 代码清理

- 删除备份文件
- 清理注释代码
- 删除未使用的旧文件
- 优化导入

验证: 编译通过，功能正常"
```

---

## 步骤 2: 性能优化

### 2.1 APK 体积优化

#### 2.1.1 启用 ProGuard/R8

打开 `app/build.gradle.kts`，确保 release 配置正确：

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

#### 2.1.2 配置 ProGuard 规则

打开 `app/proguard-rules.pro`，添加 Stream SDK 的保留规则：

```proguard
# Stream Chat SDK
-keep class io.getstream.chat.** { *; }
-keep class io.getstream.log.** { *; }
-dontwarn io.getstream.**

# 保留 Stream 的序列化类
-keepclassmembers class io.getstream.chat.android.models.** {
    <fields>;
    <init>(...);
}

# 保留 Compose 相关
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
```

#### 2.1.3 构建和测量

```bash
# 构建 Release APK
./gradlew assembleRelease

# 检查体积
ls -lh app/build/outputs/apk/release/app-release.apk

# 使用 APK Analyzer 分析
# Android Studio: Build -> Analyze APK
```

**目标**: Release APK 体积增加 ≤ 2MB

### 2.2 启动时间优化

#### 2.2.1 延迟初始化

修改 `StreamChatInitializer.kt`，使用懒加载：

```kotlin
object StreamChatInitializer {
    
    private var isInitialized = false
    private val initLock = Any()
    
    /**
     * 延迟初始化 - 仅在需要时初始化
     */
    fun ensureInitialized(context: Context) {
        if (isInitialized) return
        
        synchronized(initLock) {
            if (isInitialized) return
            initialize(context)
            isInitialized = true
        }
    }
    
    // 原有的 initialize 方法变为 private
    private fun initialize(context: Context) {
        // ... 现有初始化代码
    }
}
```

在 `AppModule.kt` 中移除立即初始化：

```kotlin
class WorkbenchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 移除: StreamChatInitializer.initialize(this)
        
        startKoin {
            androidLogger()
            androidContext(this@WorkbenchApp)
            modules(appModule)
        }
    }
}
```

在首次使用时初始化：

```kotlin
// ChatViewModel.kt
init {
    // 延迟初始化 Stream SDK
    StreamChatInitializer.ensureInitialized(application)
}
```

#### 2.2.2 测量启动时间

```bash
# 冷启动
adb shell am force-stop com.aichat.workbench
adb shell am start -W com.aichat.workbench/.MainActivity

# 记录 TotalTime 值
```

**目标**: 启动时间增加 ≤ 200ms

### 2.3 内存优化

#### 2.3.1 配置 Stream 缓存

修改 `StreamChatInitializer.kt`：

```kotlin
val offlinePluginFactory = StreamOfflinePluginFactory(
    appContext = context
).apply {
    // 限制缓存大小
    maxCachedMessages = 100 // 默认可能是 200+
}
```

#### 2.3.2 消息分页

在 `ChatViewModel.kt` 中配置分页：

```kotlin
val factory = MessagesViewModelFactory(
    context = context,
    channelId = channelId,
    messageLimit = 30, // 每次加载 30 条
    enforceUniqueReactions = true
)
```

#### 2.3.3 测量内存

```bash
# 测量初始内存
adb shell dumpsys meminfo com.aichat.workbench | grep "TOTAL:"

# 滚动 1000 条消息后再次测量
adb shell dumpsys meminfo com.aichat.workbench | grep "TOTAL:"
```

**目标**: 内存增加 ≤ 50MB

### 2.4 滚动性能优化

#### 2.4.1 启用 Compose Compiler 优化

在 `app/build.gradle.kts` 中：

```kotlin
composeCompiler {
    enableStrongSkippingMode = true
    includeSourceInformation = false // Release 构建
}
```

#### 2.4.2 优化消息渲染

如果有自定义消息组件，添加 `@Stable` 注解：

```kotlin
@Stable
data class MessageUiState(
    val id: String,
    val content: String,
    val timestamp: Long
)
```

#### 2.4.3 测量 FPS

使用 Android Studio Profiler 或命令行：

```bash
adb shell dumpsys gfxinfo com.aichat.workbench
```

**目标**: 滚动平均 FPS ≥ 55

### 2.5 提交优化

```bash
git add .
git commit -m "perf(stream): 步骤 2 - 性能优化

优化项:
- 启用 ProGuard/R8，APK 体积减少 X MB
- 延迟初始化，启动时间减少 X ms
- 优化内存配置，内存占用减少 X MB
- Compose 编译器优化，滚动更流畅

测试结果:
- APK 体积: XX MB (增加 X MB)
- 启动时间: XX ms (增加 X ms)
- 内存占用: XX MB
- 滚动 FPS: XX"
```

---

## 步骤 3: 代码质量提升

### 3.1 Lint 检查和修复

```bash
# 运行 Lint
./gradlew lint

# 查看报告
open app/build/reports/lint-results.html

# 自动修复部分问题
./gradlew lintFix
```

修复关键和高优先级的 Lint 警告。

### 3.2 格式化代码

```bash
# 如果使用 ktlint
./gradlew ktlintFormat

# 或在 Android Studio 中
Code -> Reformat Code (Ctrl+Alt+L / Cmd+Option+L)
```

### 3.3 添加文档注释

为新增的公共类和方法添加 KDoc：

```kotlin
/**
 * Stream Chat 初始化器
 * 
 * 负责初始化 Stream Chat SDK，配置离线插件和状态管理。
 * 
 * 使用方法:
 * ```kotlin
 * StreamChatInitializer.ensureInitialized(context)
 * ```
 * 
 * @see ChatClient
 */
object StreamChatInitializer {
    // ...
}
```

### 3.4 代码审查检查项

创建自检清单 `docs/implementation-guide/code-review-checklist.md`:

```markdown
# 代码审查自检清单

## 代码质量
- [ ] 没有硬编码的字符串（使用 strings.xml）
- [ ] 没有魔法数字（定义为常量）
- [ ] 函数长度合理（< 50 行）
- [ ] 类职责单一
- [ ] 命名清晰自解释

## 性能
- [ ] 没有在主线程进行重操作
- [ ] 适当使用 `remember` 避免重组
- [ ] Flow/LiveData 正确取消
- [ ] 没有内存泄漏

## 安全
- [ ] 没有暴露敏感信息到日志
- [ ] API Key 正确管理
- [ ] 用户数据正确处理

## 测试
- [ ] 关键逻辑有单元测试
- [ ] 手动测试所有功能
- [ ] 边界情况覆盖

## 文档
- [ ] 公共 API 有 KDoc
- [ ] 复杂逻辑有注释
- [ ] README 已更新
```

### 3.5 提交质量提升

```bash
git add .
git commit -m "refactor(stream): 步骤 3 - 代码质量提升

改进项:
- 修复 Lint 警告
- 格式化代码
- 添加文档注释
- 完成代码审查自检

Lint: X errors → 0 errors, X warnings → Y warnings"
```

---

## 步骤 4: 文档更新

### 4.1 更新 CLAUDE.md

打开 `CLAUDE.md`，添加 Stream Chat SDK 相关内容：

```markdown
## Architecture

### UI 层

应用使用 **Stream Chat SDK for Compose** 作为聊天界面的核心 UI 组件库：

- **MessageList**: 消息列表展示，支持流式响应和 Markdown 渲染
- **MessageComposer**: 消息输入框
- **MessageListHeader**: 聊天界面顶部栏

### 数据流

```
Room Database (持久化)
       ↓
MessageConverter (转换)
       ↓
Stream Message (内存)
       ↓
Stream UI Components (展示)
```

数据源始终是 Room，Stream SDK 仅用于 UI 展示。

### Stream SDK 配置

- **版本**: 6.5.1
- **初始化**: `StreamChatInitializer` (延迟初始化)
- **主题**: `AiChatStreamTheme` (基于 emerald 配色)
- **数据同步**: `DataSyncManager` 负责 Room ↔ Stream 同步

### 关键技术决策

- Stream SDK 仅用于 UI 组件，不连接 Stream 服务器
- 所有数据持久化在 Room，Stream 作为展示层
- 流式响应通过 `updateMessage` 实现打字机效果
```

### 4.2 更新 README.md

添加新的依赖信息：

```markdown
## 技术栈

- Android App：Kotlin、Jetpack Compose、Material 3、Room、DataStore、Retrofit、OkHttp
- **UI 组件**: Stream Chat SDK for Compose 6.5.1
- Provider：支持 OpenAI、OpenAI-compatible、New API、Sub2 API、自定义兼容接口文本聊天
```

### 4.3 创建迁移文档

创建 `docs/Stream-Chat-Migration-Notes.md`:

```markdown
# Stream Chat SDK 迁移笔记

## 迁移日期
2026-06-XX

## 迁移原因
- 采用成熟的开源设计系统
- 提升 UI 组件质量和一致性
- 减少自定义组件维护成本
- 获得更好的聊天体验

## 架构变更

### 变更前
```
Domain Layer (Message, Conversation)
       ↓
Custom UI Components (ChatMessageList, ChatInputBar, etc.)
```

### 变更后
```
Domain Layer (Message, Conversation)
       ↓
MessageConverter (转换层)
       ↓
Stream Message (内存模型)
       ↓
Stream UI Components (MessageList, MessageComposer, etc.)
```

## 关键实现

### 1. 数据转换
`MessageConverter.kt` 负责在 Domain Message 和 Stream Message 之间转换

### 2. 流式响应
通过不断更新 Stream Message 的 text 字段实现打字机效果

### 3. 主题定制
`AiChatStreamTheme.kt` 将 Material 3 颜色映射到 Stream Colors

## 性能影响

| 指标 | 迁移前 | 迁移后 | 变化 |
|------|--------|--------|------|
| APK 体积 | XX MB | YY MB | +Z MB |
| 启动时间 | XX ms | YY ms | +Z ms |
| 内存占用 | XX MB | YY MB | +Z MB |

## 已知问题和限制

1. Stream SDK 增加了 APK 体积约 2MB
2. 某些自定义功能需要额外实现
3. 学习曲线：团队需要熟悉 Stream SDK API

## 后续优化方向

- [ ] 进一步减少 APK 体积（Tree shaking）
- [ ] 优化流式响应性能
- [ ] 探索 Stream SDK 的更多功能（Reaction、Thread 等）
```

### 4.4 更新 CHANGELOG

创建或更新 `CHANGELOG.md`:

```markdown
## [Unreleased]

### Changed
- 迁移到 Stream Chat SDK for Compose 作为聊天 UI 组件库
- 重构消息列表、输入框和顶部栏组件
- 优化流式响应渲染性能

### Added
- Stream Chat SDK 6.5.1 集成
- 自定义 Stream 主题 (AiChatStreamTheme)
- Room 和 Stream 数据同步管理器

### Removed
- 旧的自定义聊天组件 (ChatMessageList, ChatInputBar)

### Performance
- APK 体积: +2MB (ProGuard 优化后)
- 启动时间: +150ms (延迟初始化)
- 内存占用: 持平
```

### 4.5 提交文档更新

```bash
git add .
git commit -m "docs(stream): 步骤 4 - 文档更新

更新内容:
- CLAUDE.md: 添加 Stream SDK 架构说明
- README.md: 更新技术栈
- 创建迁移笔记文档
- 更新 CHANGELOG

所有文档与代码同步"
```

---

## 步骤 5: 最终验证

### 5.1 完整回归测试

运行完整的测试套件：

```bash
# 单元测试
./gradlew test

# Lint 检查
./gradlew lint

# 构建 Debug 和 Release
./gradlew assembleDebug assembleRelease
```

### 5.2 手动测试清单

创建最终测试清单 `docs/implementation-guide/final-test-checklist.md`:

```markdown
# 最终测试清单

## 功能测试
- [ ] 创建新会话
- [ ] 发送消息
- [ ] 接收 AI 流式响应
- [ ] Markdown 渲染（代码块、列表、粗体等）
- [ ] 长按消息操作（复制、删除）
- [ ] 会话切换
- [ ] 搜索消息
- [ ] Provider 切换
- [ ] 深色/浅色模式切换
- [ ] 返回导航

## 性能测试
- [ ] 1000+ 条消息流畅滚动
- [ ] 应用冷启动时间 < 2s
- [ ] 内存无泄漏（使用 Profiler 验证）

## 兼容性测试
- [ ] Android 8.0 (API 26)
- [ ] Android 13 (API 33)
- [ ] Android 14 (API 34)
- [ ] 小屏设备（5 英寸）
- [ ] 大屏设备（7 英寸+）
- [ ] 横屏模式

## 边界测试
- [ ] 网络断开时发送消息
- [ ] 应用后台返回
- [ ] 极长消息（10000+ 字符）
- [ ] 空会话
- [ ] 快速连续发送多条消息

## 视觉检查
- [ ] 颜色主题一致（emerald）
- [ ] 字体大小适中
- [ ] 圆角风格统一
- [ ] 间距布局合理
- [ ] 动画流畅自然
```

执行清单中的所有项目。

### 5.3 性能基准测试

运行最终的性能测试，记录结果：

```bash
# 创建性能报告
mkdir -p docs/implementation-guide/metrics

cat > docs/implementation-guide/metrics/final-performance.md << 'EOF'
# 最终性能报告

## 测试环境
- 设备: Pixel 6
- Android 版本: 13
- 构建类型: Release
- 测试日期: 2026-06-XX

## 结果

### APK 体积
- Debug: XX.X MB
- Release: XX.X MB
- 增加: +X.X MB
- **目标**: ≤ +2MB | **状态**: ✅ / ❌

### 启动时间
- 冷启动: XXX ms
- 热启动: XX ms
- 增加: +XX ms
- **目标**: ≤ +200ms | **状态**: ✅ / ❌

### 内存占用
- 初始: XX MB
- 1000条消息后: XX MB
- 增加: +XX MB
- **目标**: ≤ +50MB | **状态**: ✅ / ❌

### 滚动性能
- 平均 FPS: XX
- 掉帧率: X%
- **目标**: ≥ 55 FPS | **状态**: ✅ / ❌

## 总体评估
所有指标达标: ✅ / ❌

## 备注
(记录任何特殊情况或观察到的问题)
EOF
```

填写实际测试数据。

---

## 完成 Phase 4

### 最终检查清单

- [ ] 所有旧代码已删除
- [ ] 性能优化完成
- [ ] Lint 警告 < 10 个
- [ ] 文档全部更新
- [ ] 所有测试通过
- [ ] 性能指标达标

### 最终提交

```bash
git add .
git commit -m "feat(stream): Phase 4 完成 - Stream Chat SDK 迁移完成 🎉

## 总结

### 完成内容
✅ 集成 Stream Chat SDK 6.5.1
✅ 重构所有聊天 UI 组件
✅ 实现 Room ↔ Stream 数据同步
✅ 优化性能和体积
✅ 完善文档

### 性能指标
- APK 体积: +X.X MB (符合目标)
- 启动时间: +XX ms (符合目标)
- 内存占用: 持平
- 滚动性能: XX FPS (符合目标)

### 迁移详情
详见: docs/Stream-Chat-Migration-Notes.md

### 测试
- 单元测试: 通过
- 功能测试: 通过
- 性能测试: 通过
- 兼容性测试: 通过

### 下一步
准备合并到 main 分支"

git push origin feature/stream-chat-ui
```

---

## 准备合并

### 创建 Pull Request

```bash
# 在 GitHub/GitLab 上创建 PR
# 标题: feat: 迁移到 Stream Chat SDK
# 描述: 使用 PR 模板
```

**PR 描述模板**:

```markdown
## 概述
将聊天 UI 组件迁移到 Stream Chat SDK for Compose，提升代码质量和用户体验。

## 变更内容
- 集成 Stream Chat SDK 6.5.1
- 替换所有自定义聊天组件为 Stream 组件
- 实现数据同步层
- 性能优化和代码清理

## 测试
- [x] 单元测试通过
- [x] 功能测试通过
- [x] 性能测试通过
- [x] 在 3 个不同 Android 版本测试

## 性能影响
| 指标 | 变化 | 目标 | 状态 |
|------|------|------|------|
| APK 体积 | +X MB | ≤ +2MB | ✅ |
| 启动时间 | +XX ms | ≤ +200ms | ✅ |
| 内存 | +X MB | ≤ +50MB | ✅ |

## 截图
(添加前后对比截图)

## 相关文档
- [迁移笔记](docs/Stream-Chat-Migration-Notes.md)
- [实施指南](docs/implementation-guide/)

## Checklist
- [x] 代码审查自检
- [x] 所有测试通过
- [x] 文档已更新
- [x] CHANGELOG 已更新
```

### 等待审查和合并

审查通过后：

```bash
# 合并到 main
git checkout main
git pull origin main
git merge feature/stream-chat-ui
git push origin main

# 打标签
git tag -a v0.30.0 -m "feat: Stream Chat SDK 迁移

主要变更:
- 集成 Stream Chat SDK 6.5.1
- 重构聊天 UI 组件
- 性能优化

详见 CHANGELOG.md"

git push origin v0.30.0
```

---

## 庆祝 🎉

恭喜！Stream Chat SDK 迁移全部完成。

**完成时间**: ___天（预估 9-15 天）

**总结**:
- ✅ Phase 1: 依赖配置
- ✅ Phase 2A: 并行开发
- ✅ Phase 2B: 验证测试
- ✅ Phase 3: 组件迁移
- ✅ Phase 4: 清理优化

**成果**:
- 更现代的 UI 组件
- 更简洁的代码库
- 更好的用户体验
- 更易维护的架构

**后续优化方向**:
1. 探索 Stream SDK 的高级功能（Reaction、Thread）
2. 进一步优化 APK 体积
3. 添加更多单元测试
4. 持续性能监控
