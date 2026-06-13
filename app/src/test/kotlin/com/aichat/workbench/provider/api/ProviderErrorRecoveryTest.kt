package com.aichat.workbench.provider.api

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderErrorRecoveryTest {

    @Test
    fun providerFailureSummary_unknownHost_mentionsNetworkAndDns() {
        val message = UnknownHostException("host.invalid").providerFailureSummary("fallback")
        assertTrue(message.contains("网络不可达"))
        assertTrue(message.contains("DNS"))
    }

    @Test
    fun providerFailureSummary_socketTimeout_mentionsRetryAndNetwork() {
        val message = SocketTimeoutException("timeout").providerFailureSummary("fallback")
        assertTrue(message.contains("超时"))
        assertTrue(message.contains("重试"))
    }

    @Test
    fun providerFailureSummary_connectException_mentionsNetworkAndBaseUrl() {
        val message = ConnectException("refused").providerFailureSummary("fallback")
        assertTrue(message.contains("连接失败"))
        assertTrue(message.contains("Base URL"))
    }

    @Test
    fun providerFailureSummary_ioException_mentionsNetwork() {
        val message = IOException("io error").providerFailureSummary("fallback")
        assertTrue(message.contains("网络请求失败"))
        assertTrue(message.contains("io error"))
    }

    @Test
    fun providerFailureSummary_ioExceptionBlankMessage_usesFallback() {
        val message = IOException("").providerFailureSummary("fallback")
        assertTrue(message.contains("网络请求失败"))
    }

    @Test
    fun providerFailureSummary_genericThrowable_usesExceptionMessage() {
        val message = RuntimeException("something broke").providerFailureSummary("fallback")
        assertTrue(message.contains("something broke"))
    }

    @Test
    fun providerFailureSummary_nullMessageThrowable_usesFallbackMessage() {
        val message = RuntimeException(null as String?).providerFailureSummary("custom fallback")
        assertTrue(message.contains("custom fallback"))
    }

    @Test
    fun recoverySummary_401_mentionsApiKey() {
        val error = ProviderError(
            code = "auth_error",
            message = "Unauthorized",
            statusCode = 401,
            retryable = false,
        )
        val summary = error.recoverySummary()
        assertTrue(summary.contains("API Key"))
    }

    @Test
    fun recoverySummary_429_mentionsRateLimit() {
        val error = ProviderError(
            code = "rate_limited",
            message = "Too many requests",
            statusCode = 429,
            retryable = true,
        )
        val summary = error.recoverySummary()
        assertTrue(summary.contains("限流"))
    }

    @Test
    fun recoverySummary_500_mentionsServerError() {
        val error = ProviderError(
            code = "server_error",
            message = "Internal server error",
            statusCode = 500,
            retryable = true,
        )
        val summary = error.recoverySummary()
        assertTrue(summary.contains("服务端异常"))
    }
}