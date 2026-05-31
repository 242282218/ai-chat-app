package com.aichat.workbench.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "provider_configs")
data class ProviderConfigEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String,
    @ColumnInfo(name = "base_url")
    val baseUrl: String,
    @ColumnInfo(name = "api_key_ref")
    val apiKeyRef: String?,
    @ColumnInfo(name = "headers_json")
    val headersJson: String,
    @ColumnInfo(name = "models_json")
    val modelsJson: String,
    @ColumnInfo(name = "default_model")
    val defaultModel: String?,
    val enabled: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
