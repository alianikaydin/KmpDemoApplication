package com.anksoft.myapplication.core.presentation.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import com.anksoft.myapplication.core.preferences.ThemeMode

// The scrims androidx.activity uses by default for the 3-button navigation bar on API 26-28.
private val LightNavigationScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkNavigationScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

@Composable
actual fun PlatformThemeEffect(themeMode: ThemeMode, darkTheme: Boolean) {
    val view = LocalView.current
    LaunchedEffect(darkTheme) {
        // No Activity (for example in a preview): nothing to update.
        val activity = view.context.findActivity() as? ComponentActivity ?: return@LaunchedEffect
        // Re-applying edge-to-edge with the drawn theme sets icon contrast and, on API 26-28,
        // the navigation bar scrim together, so the icons stay visible when the chosen theme
        // differs from the system mode.
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(LightNavigationScrim, DarkNavigationScrim) { darkTheme }
        )
        val scheme = if (darkTheme) DarkColorScheme else LightColorScheme
        activity.window.setBackgroundDrawable(ColorDrawable(scheme.background.toArgb()))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
