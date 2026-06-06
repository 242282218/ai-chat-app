# 快速优化完成进度

## M-12: hasProviderDraft 性能优化 ✅

**问题:** 每次 state 变化都会重新计算 hasProviderDraft  
**修复:** 使用 `derivedStateOf` 避免不必要的 recomposition  
**文件:** ProviderSettingsScreen.kt

**修改:**
```kotlin
// 之前
val hasProviderDraft = editingId != null || ...

// 之后
val hasProviderDraft by remember {
    derivedStateOf {
        editingId != null || ...
    }
}
```

**影响:** 减少不必要的 recomposition，提升性能

---

## L-10: derivedStateOf 优化

已通过 M-12 实施。

---

## 剩余任务: L-2 关键错误消息国际化

可选项，时间成本较高。建议推迟到 v0.25.0。
