package com.aichat.workbench.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidSecretStore(context: Context) : SecretStore {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun putSecret(ref: String, value: String) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            val iv = cipher.iv
            val committed = preferences.edit()
                .putString(ref, "${iv.toBase64()}:${ciphertext.toBase64()}")
                .commit()
            if (!committed) {
                throw SecretStoreException("密钥写入失败")
            }
        } catch (e: Exception) {
            throw SecretStoreException("加密存储失败：${e.message}", e)
        }
    }

    override suspend fun getSecret(ref: String): String? {
        val encoded = preferences.getString(ref, null) ?: return null
        val parts = encoded.split(":", limit = 2)
        if (parts.size != 2) {
            return null
        }

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = kotlin.io.encoding.Base64.UrlSafe.decode(parts[0])
            val ciphertext = kotlin.io.encoding.Base64.UrlSafe.decode(parts[1])
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw SecretStoreException("密钥解密失败：${e.message}", e)
        }
    }

    override suspend fun deleteSecret(ref: String) {
        if (!preferences.edit().remove(ref).commit()) {
            throw SecretStoreException("密钥删除失败")
        }
    }

    private fun getOrCreateKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                generator.init(spec)
                generator.generateKey()
            }

            (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } catch (e: Exception) {
            throw SecretStoreException("无法访问安全存储：${e.message}", e)
        }
    }

    private fun ByteArray.toBase64(): String =
        kotlin.io.encoding.Base64.UrlSafe.encode(this)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ai_chat_secret_master_key"
        const val PREFERENCES_NAME = "ai_chat_secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
