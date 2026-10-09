package com.anksoft.myapplication.core.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.anksoft.myapplication.core.preferences.ThemeMode

// "" removes the override so the page follows the browser again.
@JsFun("(value) => { const s = document.documentElement.style; if (value) s.setProperty('color-scheme', value); else s.removeProperty('color-scheme'); }")
private external fun setDocumentColorScheme(value: String)

@Composable
actual fun PlatformThemeEffect(themeMode: ThemeMode, darkTheme: Boolean) {
    LaunchedEffect(themeMode) {
        setDocumentColorScheme(themeMode.storedValue ?: "")
    }
}
