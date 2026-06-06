# L-2 架构问题：Domain 层不应依赖 Android 资源

## 问题

UseCase 在 domain 层，不应该依赖：
- Android Context
- R.string 资源
- 任何 Android 框架类

## 解决方案

### 方案 A: 保持硬编码（推荐）
Domain 层的错误消息保持硬编码，只在 UI 层国际化

**优点:**
- 保持 Clean Architecture
- Domain 层独立可测试
- 错误消息主要用于调试

**实施:**
只国际化 Composable 中的 UI 文本

### 方案 B: 错误码 + UI 层映射
Domain 层返回错误码，UI 层映射到本地化字符串

**缺点:**
- 复杂度高
- 需要大量重构
- 投入产出比低

### 方案 C: 依赖注入错误消息
通过构造函数注入错误消息

**缺点:**
- 破坏 domain 层的纯粹性
- 增加复杂度

## 决定

**采用方案 A**: 只国际化 UI 层文本

**理由:**
1. 保持架构清晰
2. Domain 层错误消息主要用于开发调试
3. 用户看到的是 UI 层的友好提示
4. 符合最佳实践

## 执行

- ✅ strings.xml 已添加（作为参考）
- 🎯 专注于 Composable UI 文本国际化
- 🎯 Domain 层保持现状
