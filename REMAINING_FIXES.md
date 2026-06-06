# 剩余问题修复计划

## 已完成 (16/37)
✅ C-1, C-2 (Critical)
✅ H-4, H-5, H-6, H-8 (High)
✅ M-1, M-2, M-3, M-4, M-5, M-6, M-7, M-9, M-13, M-14, M-15 (Medium)

## 待修复 Medium (5)
- [ ] M-8: HomeScreen 双重状态管理
- [ ] M-10: ImageGenerationScreen 控制状态托管
- [ ] M-11: MessageBubble expanded 状态 (已验证无问题)
- [ ] M-12: ProviderSettingsScreen hasProviderDraft 性能
- [ ] M-16: 图片生成部分成功回滚

## 待修复 Low (12)
- [ ] L-1: LazyColumn key 参数
- [ ] L-2: 硬编码字符串国际化
- [ ] L-3: Icon contentDescription
- [ ] L-4: Modifier 顺序一致性
- [ ] L-5: Composable 参数过多拆分
- [ ] L-6: @Suppress 说明原因
- [ ] L-7: 扩展函数独立文件
- [ ] L-8: 深色模式测试
- [ ] L-9: Composable Preview
- [ ] L-10: derivedStateOf 优化
- [ ] L-11: UseCase 实现增强
- [ ] L-12: domain/entity 映射封装

## 架构重构 (不在本次范围)
- H-1: ToolExecutor 移至 domain 层
- H-2: GenerationController 移至 domain 层
- H-3: ChatScreen 拆分 (2898行)
- H-7: ChatViewModel 瘦身 (536行)

## 策略
1. 快速完成可以立即修的 Medium (M-8, M-10, M-12)
2. Low 问题只修复影响无障碍和质量的 (L-1, L-3, L-9)
3. 架构重构单独规划，不在本次修复范围
