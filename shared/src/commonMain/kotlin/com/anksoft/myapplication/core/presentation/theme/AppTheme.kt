package com.anksoft.myapplication.core.presentation.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The app's theme root: color scheme plus a full-size background surface.
 * The surface only carries a test tag (no semantics), so screen readers skip it.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .testTag(if (darkTheme) ThemeTestTags.APP_THEME_DARK else ThemeTestTags.APP_THEME_LIGHT),
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }
}
