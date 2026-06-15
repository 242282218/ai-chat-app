package com.aichat.workbench.domain.exception

/**
 * Base exception for repository operations.
 */
sealed class RepositoryException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Database operation failed (e.g., SQLiteException).
 */
class DatabaseException(
    message: String = "数据库操作失败",
    cause: Throwable? = null,
) : RepositoryException(message, cause)

/**
 * File I/O operation failed (e.g., IOException, disk full, permission denied).
 */
class StorageException(
    message: String = "文件存储操作失败",
    cause: Throwable? = null,
) : RepositoryException(message, cause)

/**
 * Data format or conversion error (e.g., JSON parsing, entity mapping).
 */
class DataFormatException(
    message: String = "数据格式错误",
    cause: Throwable? = null,
) : RepositoryException(message, cause)
