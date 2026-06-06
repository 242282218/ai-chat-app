package com.aichat.workbench.domain.model

enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    fun useDarkTheme(systemDarkTheme: Boolean): Boolean =
        when (this) {
            System -> systemDarkTheme
            Light -> false
            Dark -> true
        }

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: System
    }
}
