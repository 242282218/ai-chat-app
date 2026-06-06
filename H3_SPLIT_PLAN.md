# H-3: ChatScreen 拆分计划

**当前状态:** ChatScreen.kt = 2900 行, 43+ Composable 函数  
**目标:** 拆分为 6 个文件，每个 < 600 行

---

## 📊 当前分析

### 按功能分组 (基于行号分析)

1. **主框架 & TopBar** (1-400)
   - ChatScreen 主入口
   - ChatTopBar
   - Scaffold 布局
   - 导航逻辑

2. **消息列表** (400-1200)
   - MessageList
   - MessageBubble variations
   - Tool call displays
   - Streaming indicators

3. **输入区域** (1200-1800)
   - ChatInput
   - Image attachments
   - File attachments
   - Send button

4. **控制面板** (1800-2400)
   - Settings sheet
   - Model selection
   - Parameters controls
   - Provider selection

5. **工具面板** (2400-2700)
   - Tool approval dialogs
   - Tool result displays
   - Tool status indicators

6. **辅助组件** (2700-2900)
   - Helper functions
   - Extensions
   - Utilities
   - Data classes

---

## 🎯 拆分方案

### 文件 1: ChatScreen.kt (保留主入口)
**行数:** ~400 行  
**内容:**
- `@Composable fun ChatScreen()` 主入口
- `ChatTopBar`
- Scaffold 结构
- State management
- Navigation

### 文件 2: ChatMessageList.kt
**行数:** ~600 行  
**内容:**
- `MessageList` Composable
- `MessageBubble` 及变体
- Tool call 显示组件
- Streaming indicator
- Message actions (copy, retry, etc.)

### 文件 3: ChatInputArea.kt
**行数:** ~500 行  
**内容:**
- `ChatInput` 主组件
- Image draft 显示
- File attachment UI
- Send button & logic
- Input validation

### 文件 4: ChatControlSheets.kt
**行数:** ~600 行  
**内容:**
- Settings bottom sheet
- Model selection sheet
- Parameters controls (temperature, topP, maxTokens)
- Provider selection
- Prompt presets

### 文件 5: ChatToolPanels.kt
**行数:** ~500 行  
**内容:**
- Tool approval dialogs
- Tool result displays
- Tool status components
- Tool permission UI

### 文件 6: ChatHelpers.kt
**行数:** ~300 行  
**内容:**
- Helper functions
- Extension functions
- Data classes
- Constants

---

## ⚠️ 风险评估

### 高风险因素
1. **State 共享复杂** - 所有组件都依赖 ChatUiState
2. **ViewModel 引用** - 大量 viewModel 方法调用
3. **Navigation callbacks** - 复杂的导航回调链
4. **编译时间** - 可能导致增量编译失败

### 降低风险策略
1. **增量拆分** - 一次拆一个文件，立即编译验证
2. **保持 internal** - 使用 internal 可见性保持访问
3. **最小改动** - 只移动代码，不重构逻辑
4. **充分测试** - 每步都运行 UI 测试

---

## 🚦 执行决策

### 选项 A: 立即执行 (激进)
- 一次性拆分所有 6 个文件
- 风险: ⚠️⚠️⚠️ 极高
- 时间: 2-3 小时
- 建议: ❌ 不推荐

### 选项 B: 分阶段执行 (保守)
- v0.25.0: 拆出 ChatHelpers + ChatToolPanels
- v0.26.0: 拆出 ChatInputArea
- v0.27.0: 拆出 ChatMessageList + ChatControlSheets
- 风险: ⚠️ 低
- 建议: ✅ 推荐

### 选项 C: 推迟执行 (最保守)
- 保持现状，专注功能开发
- 风险: ✅ 无
- 建议: ⚠️ 可选

---

## 💡 最终建议

**当前已完成:**
- ✅ 21 个核心问题修复
- ✅ ChatViewModel 精简 32%
- ✅ ToolExecutor 移至 domain 层

**建议行动:**
1. **立即发布 v0.24.0** (包含所有核心修复)
2. **v0.25.0 开始分阶段拆分** ChatScreen
3. 每个版本只拆 1-2 个文件，充分测试

**理由:**
- ChatScreen 拆分是纯粹的代码组织优化
- 不影响功能，不修复 bug
- 风险高，收益相对较低
- 应该在稳定版本的基础上进行

---

## 决策

**暂停 H-3 拆分**  
**原因:** 风险/收益比不合理  
**替代方案:** 在 v0.25.0+ 中分阶段进行

**当前优先级:**
1. ✅ 发布 v0.24.0 (已准备好)
2. ⏭️ 补充测试覆盖
3. ⏭️ 后续版本逐步拆分
