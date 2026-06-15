# 新建对话状态隔离修复

## 问题描述

**症状**：点击"新对话"按钮或从对话列表页面发送新消息创建对话时，新对话无法正常创建，表现为：
- 旧对话的消息仍然显示
- 输入框和状态未清空
- 新对话和旧对话共享同一个 ViewModel 实例

## 根本原因

**AppNavHost.kt:169** 中 `navigateToNewChat` 函数使用了 `launchSingleTop = true` 导航选项：

```kotlin
navigate(
    "${AppDestination.Chat.route}?$CHAT_DRAFT_ARG=...&$CHAT_DRAFT_REF_ARG=...",
) {
    launchSingleTop = true  // 问题所在
}
```

### 原因分析

1. **`launchSingleTop` 语义**：如果目标路由已在返回栈顶部，则复用该 BackStackEntry，不创建新实例
2. **路由匹配规则**：Navigation Compose 将 `chat?draft={value1}` 和 `chat?draft={value2}` 视为**同一个目的地**（路由模式相同，只是参数不同）
3. **ViewModel 作用域**：`ChatViewModel` 绑定到 BackStackEntry，复用 entry 意味着复用 ViewModel
4. **结果**：
   - 第一次打开新对话 → 创建 BackStackEntry A，创建 ViewModel A
   - 第二次打开新对话 → 因为 `launchSingleTop=true`，复用 BackStackEntry A 和 ViewModel A
   - ViewModel A 的状态（消息列表、选中对话 ID）未清空，导致显示旧内容

## 解决方案

**移除 `navigateToNewChat` 中的 `launchSingleTop = true`**，让每次新建对话都创建独立的 BackStackEntry：

```kotlin
private fun NavController.navigateToNewChat(
    draft: String,
    draftHandoffRepository: DraftHandoffRepository,
) {
    val draftRef = draft
        .takeIf { it.length > MAX_ROUTE_DRAFT_LENGTH }
        ?.let(draftHandoffRepository::put)
    val routeDraft = draft.takeIf { draftRef == null }.orEmpty()
    navigate(
        "${AppDestination.Chat.route}?$CHAT_DRAFT_ARG=${Uri.encode(routeDraft)}&$CHAT_DRAFT_REF_ARG=${Uri.encode(draftRef.orEmpty())}",
    )
    // 移除了 launchSingleTop = true
}
```

### 为什么这样修复

- **新建对话**：每次都是全新的聊天会话，需要独立的 ViewModel 实例来存储独立的状态
- **BackStackEntry 生命周期**：新 entry → 新 ViewModelStore → 新 ViewModel 实例
- **状态隔离**：不同对话的消息、输入框、UI 状态完全独立

### 其他路由保持不变

**`navigateToConversation` 保留 `launchSingleTop = true`**：

```kotlin
private fun NavController.navigateToConversation(conversationId: ConversationId) {
    navigate("${AppDestination.Chat.route}/${Uri.encode(conversationId.value)}") {
        launchSingleTop = true  // 保留，这是正确的
    }
}
```

原因：
- 打开**已存在的对话**应该复用 entry，避免重复栈累积
- 同一个 conversationId 多次打开，应该是同一个聊天界面实例

## 验证方案

### 手动测试

1. **场景 1：从对话列表创建新对话**
   - 打开对话列表页面
   - 在输入框输入消息"测试1"，点击发送
   - 等待回复
   - 点击返回，回到对话列表
   - 在输入框输入消息"测试2"，点击发送
   - **预期**：应该看到只有"测试2"的新对话，不显示"测试1"的内容

2. **场景 2：使用右上角"新对话"按钮**
   - 打开一个已有对话（有历史消息）
   - 点击右上角"+"图标创建新对话
   - **预期**：应该看到空白聊天界面，输入框为空，没有历史消息

3. **场景 3：返回栈行为**
   - 创建新对话 A
   - 发送消息到对话 A
   - 创建新对话 B
   - 发送消息到对话 B
   - 按返回键
   - **预期**：返回到对话 A，显示 A 的消息，不显示 B 的消息

### 自动化测试（可选）

```kotlin
@Test
fun `新建对话应该创建独立的 ViewModel 实例`() {
    // 1. 从对话列表导航到新对话
    composeTestRule.setContent { 
        AppNavHost() 
    }
    
    // 2. 发送消息创建对话
    composeTestRule.onNodeWithText("发消息开始新对话...").performTextInput("测试1")
    composeTestRule.onNodeWithContentDescription("发送").performClick()
    
    // 3. 返回列表
    composeTestRule.onNodeWithContentDescription("返回").performClick()
    
    // 4. 再次发送消息创建新对话
    composeTestRule.onNodeWithText("发消息开始新对话...").performTextInput("测试2")
    composeTestRule.onNodeWithContentDescription("发送").performClick()
    
    // 5. 验证只显示"测试2"，不显示"测试1"
    composeTestRule.onNodeWithText("测试1").assertDoesNotExist()
    composeTestRule.onNodeWithText("测试2").assertIsDisplayed()
}
```

## 相关代码文件

- `app/src/main/java/com/aichat/workbench/navigation/AppNavHost.kt` - 导航逻辑
- `app/src/main/java/com/aichat/workbench/feature/chat/ChatScreen.kt` - 聊天界面
- `app/src/main/java/com/aichat/workbench/feature/chat/ChatViewModel.kt` - ViewModel
- `app/src/main/java/com/aichat/workbench/app/AppModule.kt` - 依赖注入配置

## 修复日期

2026-06-15
