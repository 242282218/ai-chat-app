package com.aichat.workbench.data.crypto

interface SecretStore {
    suspend fun putSecret(ref: String, value: String)

    suspend fun getSecret(ref: String): String?

    suspend fun deleteSecret(ref: String)
}
