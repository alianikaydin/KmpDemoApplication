package com.anksoft.myapplication.core.preferences

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

actual fun deviceLanguageTag(): String =
    primaryLanguageSubtag(NSLocale.preferredLanguages.firstOrNull() as? String)
