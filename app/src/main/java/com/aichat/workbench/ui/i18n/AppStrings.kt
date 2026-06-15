package com.aichat.workbench.ui.i18n

/**
 * Internationalization strings for the app.
 * Part of Phase 5: Internationalization Support
 *
 * Temporary string facade until Android resource localization is scoped.
 */

/**
 * App strings with Chinese and English support
 */
object AppStrings {
    // Common
    const val APP_NAME_ZH = "AI 聊天助手"
    const val APP_NAME_EN = "AI Chat Assistant"

    const val OK_ZH = "确定"
    const val OK_EN = "OK"

    const val CANCEL_ZH = "取消"
    const val CANCEL_EN = "Cancel"

    const val DELETE_ZH = "删除"
    const val DELETE_EN = "Delete"

    const val CLOSE_ZH = "关闭"
    const val CLOSE_EN = "Close"

    // Chat
    const val NEW_CHAT_ZH = "新对话"
    const val NEW_CHAT_EN = "New Chat"

    const val SEND_MESSAGE_ZH = "发送消息"
    const val SEND_MESSAGE_EN = "Send Message"

    const val SEARCH_MESSAGES_ZH = "搜索消息"
    const val SEARCH_MESSAGES_EN = "Search Messages"

    const val COPY_ZH = "复制"
    const val COPY_EN = "Copy"

    const val EDIT_ZH = "编辑"
    const val EDIT_EN = "Edit"

    const val RETRY_ZH = "重试"
    const val RETRY_EN = "Retry"

    const val SHARE_ZH = "分享"
    const val SHARE_EN = "Share"

    const val REGENERATE_ZH = "重新生成"
    const val REGENERATE_EN = "Regenerate"

    // Time
    const val JUST_NOW_ZH = "刚刚"
    const val JUST_NOW_EN = "Just now"

    const val MINUTES_AGO_ZH = "分钟前"
    const val MINUTES_AGO_EN = "minutes ago"

    const val HOURS_AGO_ZH = "小时前"
    const val HOURS_AGO_EN = "hours ago"

    const val DAYS_AGO_ZH = "天前"
    const val DAYS_AGO_EN = "days ago"

    // Errors
    const val ERROR_GENERATION_FAILED_ZH = "回复生成失败"
    const val ERROR_GENERATION_FAILED_EN = "Generation failed"

    const val ERROR_NO_PROVIDER_ZH = "还没有可用的聊天模型连接"
    const val ERROR_NO_PROVIDER_EN = "No chat model connected"

    const val ERROR_COPY_FAILED_ZH = "复制失败"
    const val ERROR_COPY_FAILED_EN = "Copy failed"

    // Success
    const val SUCCESS_COPIED_ZH = "已复制到剪贴板"
    const val SUCCESS_COPIED_EN = "Copied to clipboard"

    const val SUCCESS_DELETED_ZH = "已删除"
    const val SUCCESS_DELETED_EN = "Deleted"

    // Dialogs
    const val DIALOG_DELETE_CONVERSATION_TITLE_ZH = "删除对话？"
    const val DIALOG_DELETE_CONVERSATION_TITLE_EN = "Delete conversation?"

    const val DIALOG_DELETE_CONVERSATION_MESSAGE_ZH = "删除后无法恢复。"
    const val DIALOG_DELETE_CONVERSATION_MESSAGE_EN = "This action cannot be undone."

    const val DIALOG_CLEAR_CONTEXT_TITLE_ZH = "清空上下文？"
    const val DIALOG_CLEAR_CONTEXT_TITLE_EN = "Clear context?"
}

/**
 * String provider based on locale
 * Lightweight locale switcher for code paths that are not resource-backed yet.
 */
class StringProvider(private val locale: String = "zh") {
    fun getString(key: StringKey): String {
        return when (locale) {
            "en" -> key.en
            else -> key.zh
        }
    }
}

/**
 * String key with zh and en values
 */
data class StringKey(
    val zh: String,
    val en: String
)

/**
 * Extension function to get localized string
 */
fun StringKey.localized(locale: String = "zh"): String {
    return when (locale) {
        "en" -> en
        else -> zh
    }
}
