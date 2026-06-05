package com.aichat.workbench.tool.local

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.model.ToolOutput
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalJsToolTest {
    @Test
    fun executeNormalizesLanguageCodeAndJsonInput() = runTest {
        val runner = RecordingLocalScriptRunner(
            result = LocalScriptRunResult(
                output = """{"ok":true}""",
                durationMs = 7,
                timedOut = false,
                truncated = false,
            ),
        )
        val tool = LocalJsTool(runner)

        val output = tool.execute(
            request(
                """
                    {
                      "language": "JS",
                      "code": " return input.ok; ",
                      "inputJson": " { \"ok\": true } ",
                      "timeoutMillis": 500,
                      "outputLimitBytes": 128
                    }
                """.trimIndent(),
            ),
        ).jsonOutput()

        val request = runner.requests.single()
        assertEquals(ScriptLanguage.JavaScript, request.language)
        assertEquals("return input.ok;", request.code)
        assertEquals("""{ "ok": true }""", request.inputJson)
        assertEquals(500L, request.timeoutMillis)
        assertEquals(128, request.outputLimitBytes)
        assertEquals("javascript", output.stringValue("language"))
        assertEquals("""{"ok":true}""", output.stringValue("output"))
        assertEquals(false, output.booleanValue("timedOut"))
        assertEquals(false, output.booleanValue("truncated"))
    }

    @Test
    fun executeRejectsUnsupportedLanguageBeforeRunningScript() = runTest {
        val runner = RecordingLocalScriptRunner()
        val error = assertInvalidArguments {
            LocalJsTool(runner).execute(request("""{"language":"python","code":"print(1)"}"""))
        }

        assertEquals("本地脚本第一阶段仅支持 JavaScript。", error.message)
        assertEquals(emptyList<LocalScriptRunRequest>(), runner.requests)
    }

    @Test
    fun executeRejectsInvalidInputJsonBeforeRunningScript() = runTest {
        val runner = RecordingLocalScriptRunner()
        val error = assertInvalidArguments {
            LocalJsTool(runner).execute(request("""{"code":"return input;","inputJson":"{"}"""))
        }

        assertEquals("inputJson 必须是合法 JSON。", error.message)
        assertEquals(emptyList<LocalScriptRunRequest>(), runner.requests)
    }

    @Test
    fun executeRejectsTimeoutOutsideSandboxLimit() = runTest {
        val runner = RecordingLocalScriptRunner()
        val error = assertInvalidArguments {
            LocalJsTool(runner).execute(request("""{"code":"return 1;","timeoutMillis":99}"""))
        }

        assertEquals("timeoutMillis 必须在 100 到 5000 之间。", error.message)
        assertEquals(emptyList<LocalScriptRunRequest>(), runner.requests)
    }

    @Test
    fun executeRejectsOutputLimitOutsideSandboxLimit() = runTest {
        val runner = RecordingLocalScriptRunner()
        val error = assertInvalidArguments {
            LocalJsTool(runner).execute(request("""{"code":"return 1;","outputLimitBytes":63}"""))
        }

        assertEquals("outputLimitBytes 必须在 64 到 32768 之间。", error.message)
        assertEquals(emptyList<LocalScriptRunRequest>(), runner.requests)
    }

    @Test
    fun executeKeepsTimeoutAndTruncationFlagsInOutput() = runTest {
        val runner = RecordingLocalScriptRunner(
            result = LocalScriptRunResult(
                output = "partial",
                durationMs = 1000,
                timedOut = true,
                truncated = true,
            ),
        )

        val output = LocalJsTool(runner)
            .execute(request("""{"code":"while(true) {}","timeoutMillis":1000,"outputLimitBytes":64}"""))
            .jsonOutput()

        assertEquals("partial", output.stringValue("output"))
        assertEquals(true, output.booleanValue("timedOut"))
        assertEquals(true, output.booleanValue("truncated"))
        assertEquals(1000L, runner.requests.single().timeoutMillis)
        assertEquals(64, runner.requests.single().outputLimitBytes)
    }

    @Test
    fun executeReportsUnsupportedDeviceWithoutRunningScript() = runTest {
        val runner = RecordingLocalScriptRunner(supported = false)
        val error = assertUnavailable {
            LocalJsTool(runner).execute(request("""{"code":"return 1;"}"""))
        }

        assertEquals("当前设备不支持本地 JavaScript 沙箱。", error.message)
        assertEquals(emptyList<LocalScriptRunRequest>(), runner.requests)
    }

    private fun request(arguments: String): LocalToolRequest =
        LocalToolRequest(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                id = ToolCallId("call"),
                name = "local_js",
                arguments = arguments,
            ),
        )

    private fun LocalToolExecution.jsonOutput(): String =
        (output as ToolOutput.Json).value

    private fun String.stringValue(name: String): String =
        localJsTestJson.parseToJsonElement(this)
            .jsonObject
            .getValue(name)
            .jsonPrimitive
            .content

    private fun String.booleanValue(name: String): Boolean =
        localJsTestJson.parseToJsonElement(this)
            .jsonObject
            .getValue(name)
            .jsonPrimitive
            .boolean

    private suspend fun assertInvalidArguments(
        block: suspend () -> Unit,
    ): InvalidLocalToolArgumentsException {
        return try {
            block()
            throw AssertionError("Expected InvalidLocalToolArgumentsException")
        } catch (error: InvalidLocalToolArgumentsException) {
            error
        }
    }

    private suspend fun assertUnavailable(
        block: suspend () -> Unit,
    ): LocalToolUnavailableException {
        return try {
            block()
            throw AssertionError("Expected LocalToolUnavailableException")
        } catch (error: LocalToolUnavailableException) {
            error
        }
    }
}

private class RecordingLocalScriptRunner(
    private val supported: Boolean = true,
    private val result: LocalScriptRunResult = LocalScriptRunResult(
        output = "null",
        durationMs = 0,
        timedOut = false,
        truncated = false,
    ),
) : LocalScriptRunner {
    val requests = mutableListOf<LocalScriptRunRequest>()

    override fun isSupported(): Boolean = supported

    override suspend fun run(request: LocalScriptRunRequest): LocalScriptRunResult {
        assertTrue("Unsupported runner must not execute scripts.", supported)
        requests += request
        return result
    }
}

private val localJsTestJson = Json { ignoreUnknownKeys = true }
