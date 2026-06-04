package com.aichat.workbench.tool.local

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class AndroidJavaScriptRunnerDeviceTest {
    @Test
    fun run_executesJavaScriptInDeviceSandbox() = runBlocking {
        val runner = AndroidJavaScriptRunner(ApplicationProvider.getApplicationContext())
        assumeTrue("AndroidX JavaScriptSandbox is not supported on this device.", runner.isSupported())

        val result = runner.run(
            LocalScriptRunRequest(
                language = ScriptLanguage.JavaScript,
                code = "return { ok: input.ok, doubled: input.value * 2 };",
                inputJson = """{"ok":true,"value":2}""",
                timeoutMillis = 1_000,
                outputLimitBytes = 1_024,
            ),
        )

        assertEquals("""{"ok":true,"doubled":4}""", result.output)
        assertEquals(false, result.timedOut)
        assertEquals(false, result.truncated)
    }
}
