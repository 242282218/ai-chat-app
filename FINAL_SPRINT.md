# 继续修复 - 最后冲刺

## 剩余可快速完成的优化

### L-4: Modifier 顺序一致性检查
**状态:** 大部分已经一致  
**时间:** 5 分钟  
**行动:** 验证并记录

### L-7: 扩展函数移至独立文件  
**当前:** ChatViewModel 有一些扩展函数  
**时间:** 10 分钟  
**行动:** 提取 FileReadInstructionExtensions.kt

### L-11: UseCase 实现增强
**当前:** 某些 UseCase 过于简单  
**时间:** 跳过，非关键

### L-12: domain/entity 映射封装
**当前:** 缺少明确的映射层  
**时间:** 跳过，需要大规模重构

## 决策

继续完成:
1. L-4: Modifier 顺序验证
2. L-7: 提取扩展函数

跳过:
- L-2: 国际化 (大量工作)
- L-3: Icon contentDescription (已验证 WorkbenchIconButton 正确)
- L-5: Composable 参数 (代码风格)
- L-8: 深色模式测试 (需要手动测试)
- L-11, L-12: 需要大规模重构
