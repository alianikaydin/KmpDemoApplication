package com.anksoft.myapplication.core.presentation.locale

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

@Composable
actual fun appLocaleProvides(tag: String?): ProvidedValue<*> {
    // Read above this provider, so it is always the real system configuration. It is never cached:
    // when the device language changes, "System default" must follow it (AC-8).
    val systemConfiguration = LocalConfiguration.current
    if (tag == null) {
        Locale.setDefault(systemConfiguration.locales[0])
        return LocalConfiguration provides systemConfiguration
    }
    val locale = Locale.forLanguageTag(tag)
    // Locale.getDefault() is what Compose Resources resolves against on Android. This is
    // idempotent and process-wide; docs/localization.md forbids locale-less case conversion.
    Locale.setDefault(locale)
    val configuration = remember(systemConfiguration, tag) {
        Configuration(systemConfiguration).apply { setLocale(locale) }
    }
    return LocalConfiguration provides configuration
}

actual val appLocaleNeedsContentKey: Boolean = false
