package com.aichat.workbench.feature.settings

import com.aichat.workbench.ui.component.StatusTone

internal fun dataOperationStatusLabel(
    status: String,
    isBusy: Boolean,
): String =
    when {
        isBusy -> "处理中"
        status.isDataOperationErrorStatus() -> "错误"
        else -> "完成"
    }

internal fun dataOperationStatusTone(
    status: String,
    isBusy: Boolean,
): StatusTone =
    when {
        isBusy -> StatusTone.Accent
        status.isDataOperationErrorStatus() -> StatusTone.Critical
        else -> StatusTone.Success
    }

internal fun String.isDataOperationErrorStatus(): Boolean {
    val normalized = lowercase()
    return listOf(
        "failed",
        "error",
        "must not",
        "失败",
        "错误",
        "不能为空",
        "无效",
        "不支持",
        "超过",
        "重复",
        "缺失",
    ).any(normalized::contains)
}
