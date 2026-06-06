# 继续修复 - 架构重构阶段

**目标:** 完成所有剩余问题，包括架构重构

## Phase 1: 可快速完成的优化 (先易后难)

### 立即可做
1. [ ] L-4: Modifier 顺序一致性
2. [ ] L-7: 扩展函数移至独立文件
3. [ ] M-12: hasProviderDraft 优化
4. [ ] L-10: derivedStateOf 优化

### 需要评估
5. [ ] M-16: 图片生成事务回滚
6. [ ] L-2: 硬编码字符串资源化 (部分关键字符串)

## Phase 2: 中等规模重构

### ChatViewModel 瘦身 (H-7)
**当前:** 536 行，71 个方法  
**目标:** 300 行以内
**策略:**
- 提取 ToolInstructionBuilder (工具指令生成)
- 提取 MessageDraftManager (草稿管理)
- 提取 PromptTemplateHelper (Prompt 模板)

### ToolExecutor 移至 domain (H-1)
**当前:** feature/chat/ToolExecutor.kt (701行)  
**目标:** domain/tool/ToolExecutor.kt
**策略:**
- 创建 domain/tool 包
- 移动 ToolExecutor 及相关接口
- 更新依赖注入

## Phase 3: 大规模重构 (最后)

### GenerationController 移至 domain (H-2)
**当前:** feature/chat/GenerationController.kt (638行)  
**目标:** domain/usecase/GenerateConversationResponseUseCase.kt

### ChatScreen 拆分 (H-3)
**当前:** 2898 行单文件  
**目标:** 5-6 个文件，每个 400-600 行
**拆分方案:**
- ChatScreen.kt (主框架 + TopBar)
- ChatMessageList.kt (消息列表)
- ChatInputArea.kt (输入区域)
- ChatControlsSheet.kt (控制面板)
- ChatToolPanels.kt (工具面板)
- ChatHelpers.kt (辅助函数)

## 执行策略
- 每完成一个小改动立即编译验证
- 每个文件移动后运行测试
- 大规模重构前创建 git branch
- 保持向后兼容
