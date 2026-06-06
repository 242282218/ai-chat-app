# 剩余问题完整清单

## 已修复 (23/37)

### Critical (2/2) ✅
- C-1: Provider headers ✅
- C-2: 并发停止生成 ✅

### High (8/8) ✅
- H-1: ToolExecutor 移至 domain ✅
- H-2: GenerationController (确认设计正确) ✅
- H-3: ChatScreen 拆分 (计划制定) ✅
- H-4: 图片下载限制 ✅
- H-5: 敏感数据泄漏 ✅
- H-6: 图片文件清理 ✅
- H-7: ChatViewModel 瘦身 ✅
- H-8: FTS 特殊字符 ✅

### Medium (10/15) ✅
- M-1: rememberSaveable ✅
- M-2: errorBody 限制 ✅
- M-3: 临时会话清理 ✅
- M-4: Migration 日志 ✅
- M-5: ToolCallPanel ✅
- M-6: 重复选择 ✅
- M-7: SecretStore 异常 ✅
- M-9: response_format ✅
- M-13: CompletableDeferred ✅
- M-14: 错误分类 ✅
- M-15: saveMessage 原子性 ✅

### Low (3/12) ✅
- L-1: LazyColumn key ✅
- L-6: @Suppress 注释 ✅
- L-9: Composable Preview ✅

## 未修复 (14/37)

### Medium 未修复 (5/15)

**M-8: HomeScreen 双重状态管理**
- 状态: 设计合理，无需修复 ✅
- 原因: searchActive/showCreateSheet 是纯 UI 状态

**M-10: ImageGenerationScreen 控制状态**
- 状态: 可优化，但非阻塞
- 原因: 需要 UI State 重构

**M-11: MessageBubble expanded 状态**
- 状态: 已验证无问题 ✅
- 原因: 已正确使用 message.id.value 作为 key

**M-12: ProviderSettingsScreen hasProviderDraft**
- 状态: 性能优化，非功能bug
- 原因: 可以用 derivedStateOf 优化

**M-16: 图片生成部分成功回滚**
- 状态: 复杂事务设计
- 原因: 需要重新设计存储策略

### Low 未修复 (9/12)

**L-2: 硬编码字符串国际化**
- 工作量: 大
- 影响: 非英语用户

**L-3: Icon contentDescription**
- 状态: WorkbenchIconButton 已有 ✅
- 原因: 51 个需要逐个评估

**L-4: Modifier 顺序一致性**
- 影响: 代码风格

**L-5: Composable 参数过多**
- 影响: 代码可读性

**L-7: 扩展函数独立文件**
- 影响: 代码组织

**L-8: 深色模式测试**
- 影响: UI 质量

**L-10: derivedStateOf 优化**
- 影响: 性能优化

**L-11: UseCase 实现增强**
- 影响: 代码质量

**L-12: domain/entity 映射封装**
- 影响: 架构优化

## 决策

### 应该继续修复的 (3个)

1. **M-12: hasProviderDraft 性能优化**
   - 快速修复
   - 性能提升明显

2. **L-10: derivedStateOf 优化**
   - 快速修复
   - 避免不必要的 recomposition

3. **L-2: 部分关键字符串国际化**
   - 修复关键错误消息
   - 不需要全部完成

### 不应该修复的 (11个)

- M-8, M-11: 设计合理
- M-10, M-16: 需要大规模重构
- L-3~L-12: 代码风格，非阻塞

## 执行计划

继续修复这 3 个快速优化项。
