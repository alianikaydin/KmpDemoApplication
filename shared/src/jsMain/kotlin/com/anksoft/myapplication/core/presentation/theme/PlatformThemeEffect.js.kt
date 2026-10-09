package com.anksoft.myapplication.core.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.anksoft.myapplication.core.preferences.ThemeMode
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

@Composable
actual fun PlatformThemeEffect(themeMode: ThemeMode, darkTheme: Boolean) {
    LaunchedEffect(themeMode) {
        val style = (document.documentElement as? HTMLElement)?.style ?: return@LaunchedEffect
        val value = themeMode.storedValue
        // SYSTEM removes the override so the page follows the browser again.
        if (value == null) style.removeProperty("color-scheme") else style.setProperty("color-scheme", value)
    }
}
