package com.anksoft.myapplication.core.presentation.theme

import androidx.compose.runtime.Composable
import com.anksoft.myapplication.core.preferences.ThemeMode

/**
 * Applies the theme to what Compose does not draw: system bars and window background (Android),
 * the window's interface style for status bar and keyboard (iOS), the page color-scheme (web).
 * [themeMode] is needed besides [darkTheme] because SYSTEM must hand control back to the OS.
 */
@Composable
expect fun PlatformThemeEffect(themeMode: ThemeMode, darkTheme: Boolean)
