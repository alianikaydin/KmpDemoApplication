package com.anksoft.myapplication.core.preferences

import kotlinx.browser.window

actual fun deviceLanguageTag(): String {
    val language: dynamic = window.asDynamic().navigator.language
    return primaryLanguageSubtag(if (jsTypeOf(language) == "string") language as String else null)
}
