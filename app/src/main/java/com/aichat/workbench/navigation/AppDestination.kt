package com.aichat.workbench.navigation

sealed class AppDestination(
    val route: String,
    val label: String,
    val description: String,
) {
    data object Home : AppDestination("home", "首页", "主工作台")
    data object Chat : AppDestination("chat", "聊天", "开始或继续一个会话")
    data object Providers : AppDestination("providers", "Providers", "配置模型 Provider")
    data object Prompts : AppDestination("prompts", "Prompts", "管理本地 Prompt 预设")
    data object Images : AppDestination("images", "图片", "生成并查看图片")
    data object Tools : AppDestination("tools", "Tools", "配置可选 Gateway 工具")
    data object Settings : AppDestination("settings", "设置", "管理 App 数据和隐私")

    companion object {
        val topLevel = listOf(Chat, Providers, Prompts, Images, Tools, Settings)
    }
}
