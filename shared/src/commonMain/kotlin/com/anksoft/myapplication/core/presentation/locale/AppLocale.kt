package com.anksoft.myapplication.core.presentation.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key
import com.anksoft.myapplication.core.preferences.AppLanguage

/**
 * Makes the Compose Resources use [tag] ("tr", "en") instead of the device language, or the
 * device language again when [tag] is null. Each platform reads its language from a different
 * place, so each platform has its own actual:
 * - Android: the `LocalConfiguration` (its locale is replaced, the system one is re-read every time).
 * - iOS: the `AppleLanguages` user default, written from [com.anksoft.myapplication.core.preferences.AppPreferences] on every composition.
 * - Web: `window.__setAppLocale`, defined by the shim in `index.html`, which overrides `navigator.languages`.
 *
 * [com.anksoft.myapplication.core.preferences.AppPreferences] stays the only source of truth; the platform values only mirror it.
 */
@Composable
expect fun appLocaleProvides(tag: String?): ProvidedValue<*>

/**
 * True when the platform does not re-read strings by itself after [appLocaleProvides] changes,
 * so the screen content must be re-created for the new language to show.
 *
 * Android is false: its locale travels through `LocalConfiguration`, a CompositionLocal, so
 * readers recompose (to be confirmed on a device). iOS and web are true: their locale is not
 * Compose state. If a target shows old text after a switch, set its actual to true.
 */
expect val appLocaleNeedsContentKey: Boolean

/** Applies [language] to everything inside [content]. Put it above the Navigator. */
@Composable
fun ProvideAppLocale(language: AppLanguage, content: @Composable () -> Unit) {
    CompositionLocalProvider(appLocaleProvides(language.tag), content = content)
}

/**
 * Re-creates [content] when [language] changes, on platforms that need it. Wrap only the
 * content of a screen, never the Navigator: re-creating the Navigator would reset the back stack
 * and throw the user out of the Settings screen they just changed the language on. ScreenModels
 * live outside the composition, so the screen keeps its state; only `remember`ed UI state resets.
 */
@Composable
fun LocaleKeyedContent(language: AppLanguage, content: @Composable () -> Unit) {
    if (appLocaleNeedsContentKey) {
        key(language) { content() }
    } else {
        content()
    }
}
