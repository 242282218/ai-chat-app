package com.aichat.workbench.data.crypto

interface SecretStore {
    suspend fun putSecret(ref: String, value: String)

    suspend fun getSecret(ref: String): String?

    suspend fun deleteSecret(ref: String)
}

/**
 * Thrown when secret storage operations fail (e.g. keystore unavailable,
 * encryption/decryption errors, device tampered).
 */
class SecretStoreException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
