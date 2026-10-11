package com.anksoft.myapplication.core.preferences

import android.content.res.Resources

// The system configuration, not Locale.getDefault(): the app sets the default to its own choice.
actual fun deviceLanguageTag(): String =
    primaryLanguageSubtag(Resources.getSystem().configuration.locales[0]?.language)
