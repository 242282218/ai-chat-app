package com.aichat.workbench.tool.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalScriptRunnerTest {
    @Test
    fun truncateUtf8KeepsOutputWithinByteLimit() {
        val result = truncateUtf8("abc你好", limitBytes = 6)

        assertEquals("abc你", result.text)
        assertEquals(6, result.byteCount)
        assertTrue(result.truncated)
    }

    @Test
    fun sandboxScriptEmbedsInputJsonAsStringLiteral() {
        val script = LocalScriptRunRequest(
            language = ScriptLanguage.JavaScript,
            code = "return input.value;",
            inputJson = """{"value":"x\"}); globalThis.pwned = true; //"}""",
            timeoutMillis = 1000,
            outputLimitBytes = 1024,
        ).toSandboxScript()

        assertTrue(script.contains("const input = JSON.parse("))
        assertTrue(script.contains("""{\"value\":\"x\\\"}); globalThis.pwned = true; //\"}"""))
        assertTrue(script.contains("""const userCode = "return input.value;""""))
    }
}
