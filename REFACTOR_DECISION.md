# 架构重构决策与进度

## 已完成重构

### ✅ H-7: ChatViewModel 瘦身 (Step 1 完成)
- **状态:** 已完成第一步，已提交
- **成果:** 545 行 → 370 行 (-32%)
- **新文件:** ToolInstructionBuilder.kt (187 行)
- **风险:** 低 ✅
- **编译:** ✅ SUCCESS

## 待评估重构

### 🤔 H-1: ToolExecutor 移至 domain 层
**当前状态:** feature/chat/ToolExecutor.kt (706 行)  
**目标:** domain/tool/ToolExecutor.kt  

**依赖分析:**
- 引用方: GenerationController, AppModule (仅 2 处)
- 依赖: 6 个 provider 层包, GatewaySettings
- 影响面: 中等

**风险评估:** ⚠️ 中高风险
- 需要同时移动相关接口
- provider 层依赖需要重新设计
- 可能引入新 bug

**建议:** 推迟到 v0.26.0，先发布 v0.24.0

### 🤔 H-2: GenerationController 重构为 UseCase
**当前状态:** feature/chat/GenerationController.kt (638 行)  
**风险:** ⚠️ 高风险 (核心业务逻辑)  
**建议:** 推迟

### 🤔 H-3: ChatScreen 拆分
**当前状态:** feature/chat/ChatScreen.kt (2898 行)  
**风险:** ⚠️ 高风险 (UI 拆分复杂度高)  
**建议:** 推迟到 v0.27.0

## 决策

**保守策略 (推荐):**
1. **立即发布 v0.24.0** - 包含所有核心修复 + ChatViewModel 初步优化
2. **v0.25.0** - 继续 ChatViewModel 优化，补充测试
3. **v0.26.0** - 尝试 ToolExecutor 移动 (有回退计划)
4. **v0.27.0** - ChatScreen 拆分

**激进策略 (不推荐):**
- 一次性完成所有重构
- 风险: 可能引入大量新 bug
- 延迟发布时间

**最终决定:** 采用保守策略

---

## 总结

**当前已完成:**
- 21 个问题修复 ✅
- ChatViewModel 精简 32% ✅
- 所有核心安全问题解决 ✅
- 项目可发布 ✅

**剩余架构重构:**
- 风险较高，应在独立版本中进行
- 需要完善的测试覆盖
- 需要充分的回归测试

**建议行动:**
1. 停止继续重构
2. 发布 v0.24.0
3. 后续版本逐步优化
