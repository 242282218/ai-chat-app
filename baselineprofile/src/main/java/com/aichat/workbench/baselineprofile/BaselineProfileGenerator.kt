package com.aichat.workbench.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        profileBlock = {
            startActivityAndWait()
            device.wait(Until.hasObject(By.text("图片")), UI_TIMEOUT_MS)
            device.findObject(By.text("图片"))?.click()
            device.waitForIdle()
            device.findObject(By.text("模型"))?.click()
            device.waitForIdle()
            device.findObject(By.text("对话"))?.click()
            device.waitForIdle()
        },
    )

    private companion object {
        const val PACKAGE_NAME = "com.aichat.workbench"
        const val UI_TIMEOUT_MS = 5_000L
    }
}
