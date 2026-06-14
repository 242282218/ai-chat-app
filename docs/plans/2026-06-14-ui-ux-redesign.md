# UI/UX 全面重构设计文档

日期：2026-06-14
状态：执行中

## 设计目标

从"功能完备"进化到"精致体验"。参考 Apple Human Interface Guidelines 和 Material 3 最佳实践，打造轻盈、现代、有呼吸感的 AI 聊天界面。

## 核心原则

1. **Less is More** — 减少视觉噪音，留白即信息
2. **Content First** — 对话内容是主角，UI 是配角
3. **Physics-based Motion** — 用弹簧和惯性代替线性动画
4. **Progressive Disclosure** — 渐进式展示，不一次性铺满
5. **Consistent Hierarchy** — 视觉层级清晰：内容 > 操作 > 装饰

## 重构范围

### Phase 1: 设计系统基础
- 主题色从森林绿升级为更精致的石墨灰+翡翠绿双色调
- 字体排版微调：更轻的字重、更大的行距
- 形状系统：更圆润的圆角（模拟 iOS 风格）
- 动画规范：统一的 spring spec 和 duration

### Phase 2: 聊天界面重写
- ChatScreen：更简洁的布局，去掉冗余装饰
- ChatMessageBubble：气泡尾部设计、更好的间距、头像升级
- ChatInputBar：iOS 风格浮动输入框，圆角更大，阴影更柔和
- MessageActionRow：更紧凑的操作栏

### Phase 3: 对话列表重设计
- ConversationsScreen：大标题折叠、更宽松的列表间距
- 对话行：更大的头像、更清晰的信息层级
- 空状态：更优雅的插图

### Phase 4: 导航与转场
- AppBottomBar：更精致的选中态、更好的分隔
- 页面转场：更流畅的动画曲线

### Phase 5: 图片生成与设置
- ImageGenerationScreen：更清晰的表单布局
- ProviderSettingsScreen：更一致的设置项样式

## 文件影响清单

### 重写文件（核心）
1. `ui/theme/Theme.kt` — 色彩系统、字体、形状
2. `feature/chat/ChatScreen.kt` — 聊天主界面
3. `feature/chat/ChatMessageBubble.kt` — 消息气泡
4. `feature/chat/ChatInputBar.kt` — 输入栏
5. `feature/conversations/ConversationsScreen.kt` — 对话列表
6. `navigation/AppBottomBar.kt` — 底部导航

### 修改文件（适配）
7. `ui/component/MessageBubble.kt` — 线性消息气泡
8. `ui/component/MessageAvatars.kt` — 头像系统
9. `ui/component/WorkbenchButtons.kt` — 按钮组件
10. `ui/component/WorkbenchLayout.kt` — 布局组件
11. `ui/component/WorkbenchFeedback.kt` — 反馈组件
12. `ui/component/WorkbenchTextField.kt` — 输入框
13. `ui/component/StatusTone.kt` — 状态色调
14. `ui/markdown/MarkdownMessageContent.kt` — Markdown 渲染
15. `feature/image/ImageGenerationScreen.kt` — 图片生成
16. `feature/provider/ProviderSettingsScreen.kt` — 设置页
17. `feature/provider/ProviderSettingsContent.kt` — 设置内容

### 不变文件（保持）
- data/ 层全部不变
- domain/ 层全部不变
- ViewModel 逻辑不变（只改 UI 层）
- 测试文件暂不改动（等 UI 稳定后统一适配）

## 验证方式

每完成一个 Phase 后运行：
```
.\gradlew.bat compileDebugKotlin --no-daemon
```
全部完成后运行：
```
.\gradlew.bat testDebugUnitTest lint assembleDebug --no-daemon --stacktrace
```
