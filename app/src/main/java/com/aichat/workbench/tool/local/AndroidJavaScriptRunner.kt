package com.aichat.workbench.tool.local

import android.content.Context
import androidx.javascriptengine.JavaScriptSandbox
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString

class AndroidJavaScriptRunner(
    private val context: Context,
) : LocalScriptRunner {
    override fun isSupported(): Boolean =
        JavaScriptSandbox.isSupported()

    override suspend fun run(request: LocalScriptRunRequest): LocalScriptRunResult {
        if (request.language != ScriptLanguage.JavaScript) {
            throw LocalScriptExecutionException("仅支持 JavaScript。")
        }
        if (!isSupported()) {
            throw LocalScriptUnavailableException("当前设备不支持本地 JavaScript 沙箱。")
        }

        val startedNanos = System.nanoTime()
        val result = withTimeoutOrNull(request.timeoutMillis) {
            executeInSandbox(request)
        }
        val durationMs = ((System.nanoTime() - startedNanos) / 1_000_000).coerceAtLeast(0)
        return result?.copy(durationMs = durationMs)
            ?: LocalScriptRunResult(
                output = "",
                durationMs = durationMs,
                timedOut = true,
                truncated = false,
            )
    }

    private suspend fun executeInSandbox(request: LocalScriptRunRequest): LocalScriptRunResult {
        var sandbox: JavaScriptSandbox? = null
        var isolate: androidx.javascriptengine.JavaScriptIsolate? = null
        return try {
            sandbox = JavaScriptSandbox.createConnectedInstanceAsync(context).await()
            isolate = sandbox.createIsolate()
            val output = isolate.evaluateJavaScriptAsync(request.toSandboxScript()).await()
            val truncated = truncateUtf8(output, request.outputLimitBytes)
            LocalScriptRunResult(
                output = truncated.text,
                durationMs = 0,
                timedOut = false,
                truncated = truncated.truncated,
            )
        } catch (error: Throwable) {
            throw LocalScriptExecutionException(
                message = error.message ?: "JavaScript 执行失败。",
                cause = error,
            )
        } finally {
            runCatching { isolate?.close() }
            runCatching { sandbox?.close() }
        }
    }

    private fun LocalScriptRunRequest.toSandboxScript(): String {
        val input = inputJson?.trim()?.takeIf { it.isNotBlank() } ?: "null"
        val codeLiteral = localToolJson.encodeToString(code)
        return """
            (async () => {
              const input = $input;
              const userCode = $codeLiteral;
              const fn = new Function("input", `"use strict";
            ${'$'}{userCode}`);
              const value = await fn(input);
              if (typeof value === "string") return value;
              return JSON.stringify(value ?? null);
            })()
        """.trimIndent()
    }
}

private suspend fun <T> ListenableFuture<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(get())
                } catch (error: ExecutionException) {
                    continuation.resumeWithException(error.cause ?: error)
                } catch (error: Throwable) {
                    continuation.resumeWithException(error)
                }
            },
            DirectExecutor,
        )
        continuation.invokeOnCancellation {
            cancel(true)
        }
    }

private object DirectExecutor : Executor {
    override fun execute(command: Runnable) {
        command.run()
    }
}
