package com.aichat.workbench.provider.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiProviderErrorsTest {

    @Test
    fun parseOpenAiHttpError_mapsAuthenticationFailure() {
        val error = parseOpenAiHttpError(
            statusCode = 401,
            body = """{"error":{"message":"bad key"}}""",
        )

        assertEquals("authentication_failed", error.code)
        assertEquals("bad key", error.message)
        assertEquals(401, error.statusCode)
        assertFalse(error.retryable)
    }

    @Test
    fun parseOpenAiHttpError_mapsModelErrorsFromProviderMessage() {
        val error = parseOpenAiHttpError(
            statusCode = 400,
            body = """{"error":{"message":"model does not exist"}}""",
        )

        assertEquals("invalid_model", error.code)
        assertEquals("model does not exist", error.message)
        assertFalse(error.retryable)
    }

    @Test
    fun parseOpenAiHttpError_usesFallbackForMalformedBody() {
        val error = parseOpenAiHttpError(
            statusCode = 503,
            body = "{bad json",
            fallbackMessage = "图片生成请求失败：HTTP 503。",
        )

        assertEquals("provider_unavailable", error.code)
        assertEquals("图片生成请求失败：HTTP 503。", error.message)
        assertTrue(error.retryable)
    }
}
