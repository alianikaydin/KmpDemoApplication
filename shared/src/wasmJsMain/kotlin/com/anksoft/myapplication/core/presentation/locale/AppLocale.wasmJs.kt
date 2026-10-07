package com.anksoft.myapplication.core.presentation.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalAppLanguageTag = staticCompositionLocalOf<String?> { null }

// Defined by the shim in webApp/src/webMain/resources/index.html; "" means browser language.
@JsFun("(tag) => { if (typeof window.__setAppLocale === 'function') window.__setAppLocale(tag); }")
private external fun setAppLocale(tag: String)

@Composable
actual fun appLocaleProvides(tag: String?): ProvidedValue<*> {
    setAppLocale(tag ?: "")
    return LocalAppLanguageTag provides tag
}

actual val appLocaleNeedsContentKey: Boolean = true
