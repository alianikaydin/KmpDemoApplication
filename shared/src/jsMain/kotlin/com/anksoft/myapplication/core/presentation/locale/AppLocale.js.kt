package com.anksoft.myapplication.core.presentation.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.browser.window

private val LocalAppLanguageTag = staticCompositionLocalOf<String?> { null }

@Composable
actual fun appLocaleProvides(tag: String?): ProvidedValue<*> {
    // Defined by the shim in webApp/src/webMain/resources/index.html; "" means browser language.
    val setAppLocale = window.asDynamic().__setAppLocale
    if (jsTypeOf(setAppLocale) == "function") {
        setAppLocale(tag ?: "")
    }
    return LocalAppLanguageTag provides tag
}

actual val appLocaleNeedsContentKey: Boolean = true
