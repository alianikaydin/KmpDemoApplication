package com.anksoft.myapplication.core.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.anksoft.myapplication.core.preferences.ThemeMode
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

@Composable
actual fun PlatformThemeEffect(themeMode: ThemeMode, darkTheme: Boolean) {
    // SYSTEM hands control back to the OS; the override is set on the windows only and is
    // never written to NSUserDefaults, so it cannot outlive the app's own preference.
    LaunchedEffect(themeMode) {
        val style = when (themeMode) {
            ThemeMode.SYSTEM -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
            ThemeMode.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
            ThemeMode.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
        }
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
            .forEach { window -> window.overrideUserInterfaceStyle = style }
    }
}
