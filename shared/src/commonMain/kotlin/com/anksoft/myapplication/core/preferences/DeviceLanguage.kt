package com.anksoft.myapplication.core.preferences

/**
 * The device's primary language as a lower-case subtag (`"tr"`, `"en"`), or `"en"` when the
 * platform gives none. Used when the app is on "System default" and a language must be sent to the
 * backend.
 */
expect fun deviceLanguageTag(): String

/** Reduces a platform locale string such as `tr-TR`, `en_US` or `TR` to its language subtag. */
internal fun primaryLanguageSubtag(raw: String?): String =
    raw?.trim()
        ?.substringBefore('-')
        ?.substringBefore('_')
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }
        ?: FALLBACK_LANGUAGE

private const val FALLBACK_LANGUAGE = "en"
