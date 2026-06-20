# UI 整体感优化修复

## 修复的问题

### 1. 状态栏和底部不协调
**根因**：`enableEdgeToEdge()` 开启了全面屏模式，但 `ChatInputBar` 没有处理底部安全区域（导航栏）

**修复**：为 `InputBar` 的 `Surface` 添加 `.navigationBarsPadding()`，确保输入框不被系统导航栏遮挡

**影响文件**：
- `ChatInputBar.kt`：在 Surface modifier 添加 navigationBarsPadding

### 2. 输入法弹出黑色闪烁
**根因**：`ModalBottomSheet` 的默认 scrim（遮罩层）在键盘弹出时会短暂显示黑色，造成视觉闪烁

**修复方案**：
- 为所有 `ModalBottomSheet` 显式设置 `containerColor = MaterialTheme.colorScheme.surface`，确保背景色与主题一致
- 在 `ProviderSettingsScreen` 添加 `focusManager.clearFocus()`，关闭 sheet 时收起键盘，避免键盘残留引起的闪烁

**影响文件**：
- `ProviderSettingsScreen.kt`
- `ChatScreen.kt`
- `MessageActionsSheet.kt`

### 3. ModalBottomSheet 下滑容易退出
**根因**：`ModalBottomSheet` 内部的 `LazyColumn` 与 sheet 的拖拽手势冲突，用户在列表顶部下滑时会意外触发 sheet 关闭

**当前状态**：
- 添加了 `top = 16.dp` 的 contentPadding，增加顶部缓冲区
- 使用 `skipPartiallyExpanded = true`，sheet 完全展开，减少误触

**后续优化方向**（如仍不满意）：
1. 实现 `NestedScrollConnection`，只有列表滚动到顶部且继续下拉时才关闭 sheet
2. 调整 `dragHandle` 位置或样式，引导用户从 handle 区域拖拽
3. 增加关闭阈值（需要自定义 sheet 行为）

## 验证清单

- [ ] 打开聊天界面，输入框底部与系统导航栏之间有正确的间距
- [ ] 在"添加模型连接"表单中输入文字，键盘弹出时无黑色闪烁
- [ ] 在"添加模型连接"表单顶部下滑，不会轻易关闭 sheet（需要较大幅度拖拽）
- [ ] 长按消息打开操作 sheet，确认无黑色闪烁
- [ ] 关闭所有 sheet 时，界面过渡流畅，无颜色跳变

## 技术细节

### navigationBarsPadding 处理
```kotlin
// 输入栏直接在 Surface 上添加
Surface(
    modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding(),  // 避免被系统导航栏遮挡
    // ...
)

// ModalBottomSheet 内容需要手动添加
LazyColumn(
    modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding(),  // 确保底部内容可见
    // ...
)
```

### ModalBottomSheet 配置
```kotlin
ModalBottomSheet(
    onDismissRequest = { 
        focusManager.clearFocus()  // 先收起键盘
        viewModel.dismissEditor()
    },
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.surface,  // 避免闪烁
) { /* content */ }
```

## 相关文件
- `app/src/main/java/com/aichat/workbench/feature/chat/ChatInputBar.kt`
- `app/src/main/java/com/aichat/workbench/feature/provider/ProviderSettingsScreen.kt`
- `app/src/main/java/com/aichat/workbench/feature/chat/ChatScreen.kt`
- `app/src/main/java/com/aichat/workbench/feature/chat/message/MessageActionsSheet.kt`
