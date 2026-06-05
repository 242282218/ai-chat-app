package com.aichat.workbench.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "model_role_preferences",
    foreignKeys = [
        ForeignKey(
            entity = ProviderConfigEntity::class,
            parentColumns = ["id"],
            childColumns = ["provider_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["provider_id", "role"], unique = true),
        Index(value = ["provider_id"]),
    ],
)
data class ModelRolePreferenceEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "provider_id")
    val providerId: String,
    val role: String,
    val model: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
