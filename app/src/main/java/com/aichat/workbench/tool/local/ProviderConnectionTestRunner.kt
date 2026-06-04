package com.aichat.workbench.tool.local

import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.provider.api.ProviderConnectionTester

interface ProviderConnectionTestRunner {
    suspend fun test(provider: ProviderConfig, apiKey: String?): ProviderConnectionTestResult
}

data class ProviderConnectionTestResult(
    val ok: Boolean,
    val statusCode: Int?,
    val message: String,
)

class DefaultProviderConnectionTestRunner(
    private val tester: ProviderConnectionTester,
) : ProviderConnectionTestRunner {
    override suspend fun test(provider: ProviderConfig, apiKey: String?): ProviderConnectionTestResult {
        val result = tester.test(provider, apiKey)
        return ProviderConnectionTestResult(
            ok = result.ok,
            statusCode = result.statusCode,
            message = result.message,
        )
    }
}

class UnsupportedProviderConnectionTestRunner : ProviderConnectionTestRunner {
    override suspend fun test(provider: ProviderConfig, apiKey: String?): ProviderConnectionTestResult {
        throw LocalToolUnavailableException("当前环境不支持测试 Provider 连接。")
    }
}
