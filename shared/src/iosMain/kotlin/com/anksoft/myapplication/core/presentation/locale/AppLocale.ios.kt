package com.anksoft.myapplication.core.presentation.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSUserDefaults

private const val APPLE_LANGUAGES_KEY = "AppleLanguages"

private val LocalAppLanguageTag = staticCompositionLocalOf<String?> { null }

@Composable
actual fun appLocaleProvides(tag: String?): ProvidedValue<*> {
    // The in-app choice is the single source on iOS: it is written on every composition, so a
    // language picked in the iOS Settings app is replaced on the next launch when the app is on
    // "System default" (the key is removed). Documented in docs/localization.md.
    val defaults = NSUserDefaults.standardUserDefaults
    if (tag == null) {
        defaults.removeObjectForKey(APPLE_LANGUAGES_KEY)
    } else {
        defaults.setObject(listOf(tag), forKey = APPLE_LANGUAGES_KEY)
    }
    return LocalAppLanguageTag provides tag
}

actual val appLocaleNeedsContentKey: Boolean = true
