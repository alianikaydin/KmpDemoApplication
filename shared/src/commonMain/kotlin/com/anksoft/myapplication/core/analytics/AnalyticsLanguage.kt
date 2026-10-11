package com.anksoft.myapplication.core.analytics

import com.anksoft.myapplication.core.preferences.AppLanguage

/** Matches the default `values/strings.xml`, which the UI falls back to for any other language. */
private const val FALLBACK_TAG = "en"

/**
 * The language the UI is shown in, as a fixed tag from [AppLanguage] ("tr", "en"). On "System
 * default" the device language is used when the app supports it; otherwise the UI falls back to
 * the default strings (English), so the value is "en".
 *
 * [deviceTag] is only called on "System default".
 */
internal fun analyticsLanguage(choice: AppLanguage, deviceTag: () -> String): String {
    val shown = if (choice == AppLanguage.SYSTEM) AppLanguage.fromTag(deviceTag()) else choice
    return shown.tag ?: FALLBACK_TAG
}
