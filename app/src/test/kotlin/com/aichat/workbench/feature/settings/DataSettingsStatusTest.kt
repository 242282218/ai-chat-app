package com.aichat.workbench.feature.settings

import com.aichat.workbench.ui.component.StatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSettingsStatusTest {
    @Test
    fun operationStatusUsesBusyToneFirst() {
        assertEquals("处理中", dataOperationStatusLabel("导出就绪", isBusy = true))
        assertEquals(StatusTone.Accent, dataOperationStatusTone("导出就绪", isBusy = true))
    }

    @Test
    fun operationStatusTreatsKnownSuccessMessagesAsCompleted() {
        assertEquals("完成", dataOperationStatusLabel("导出就绪", isBusy = false))
        assertEquals(StatusTone.Success, dataOperationStatusTone("导入完成", isBusy = false))
        assertFalse("导出已保存".isDataOperationErrorStatus())
    }

    @Test
    fun operationStatusRecognizesBackupValidationErrors() {
        val errors = listOf(
            "备份 JSON 无效。",
            "不支持的备份版本：2。",
            "导入模型连接数量超过 50 个限制。",
            "模型偏好 Provider/模型 重复：openai/gpt-4。",
            "API Key 缺失。",
            "export failed",
        )

        errors.forEach { status ->
            assertTrue(status.isDataOperationErrorStatus())
            assertEquals("错误", dataOperationStatusLabel(status, isBusy = false))
            assertEquals(StatusTone.Critical, dataOperationStatusTone(status, isBusy = false))
        }
    }
}
