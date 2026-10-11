package com.anksoft.myapplication.core.preferences

@JsFun("() => (typeof navigator !== 'undefined' && typeof navigator.language === 'string') ? navigator.language : ''")
private external fun browserLanguage(): String

actual fun deviceLanguageTag(): String = primaryLanguageSubtag(browserLanguage())
