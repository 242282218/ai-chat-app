package com.aichat.workbench.navigation

sealed class AppDestination(
    val route: String,
    val label: String,
    val description: String,
) {
    data object Home : AppDestination("home", "首页", "会话中心")
    data object Chat : AppDestination("chat", "聊天", "开始或继续一个会话")
    data object Providers : AppDestination("providers", "模型连接", "配置模型服务")
    data object Prompts : AppDestination("prompts", "提示词", "管理本地提示词预设")
    data object Images : AppDestination("images", "图片", "生成并查看图片")
    data object Tools : AppDestination("tools", "工具", "配置可选工具网关")
    data object Settings : AppDestination("settings", "设置", "管理应用数据和隐私")

    companion object {
        val topLevel = listOf(Chat, Providers, Prompts, Images, Tools, Settings)
        val management = listOf(Providers, Prompts, Images, Tools, Settings)
    }
}
