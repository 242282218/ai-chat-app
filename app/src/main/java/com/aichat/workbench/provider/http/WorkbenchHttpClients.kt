package com.aichat.workbench.provider.http

import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient

object WorkbenchHttpClients {
    fun json(): OkHttpClient =
        baseBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()

    fun streaming(): OkHttpClient =
        baseBuilder()
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()

    fun longRunning(): OkHttpClient =
        baseBuilder()
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES)
            .build()

    private fun baseBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = 10,
                    keepAliveDuration = 3,
                    timeUnit = TimeUnit.MINUTES
                )
            )
            .addInterceptor(defaultHeadersInterceptor())

    private fun defaultHeadersInterceptor(): Interceptor =
        Interceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", USER_AGENT)
                .apply {
                    if (original.header("X-Request-Id").isNullOrBlank()) {
                        header("X-Request-Id", UUID.randomUUID().toString())
                    }
                }
                .build()
            chain.proceed(request)
        }
}

private const val USER_AGENT = "AIChatWorkbench Android"
