package com.aichat.workbench.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun fromStorageFallsBackToSystemForBlankOrUnknownValues() {
        assertEquals(ThemeMode.System, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.System, ThemeMode.fromStorage(""))
        assertEquals(ThemeMode.System, ThemeMode.fromStorage("FutureMode"))
    }

    @Test
    fun useDarkThemeRespectsMode() {
        assertTrue(ThemeMode.System.useDarkTheme(systemDarkTheme = true))
        assertFalse(ThemeMode.System.useDarkTheme(systemDarkTheme = false))
        assertFalse(ThemeMode.Light.useDarkTheme(systemDarkTheme = true))
        assertTrue(ThemeMode.Dark.useDarkTheme(systemDarkTheme = false))
    }
}
