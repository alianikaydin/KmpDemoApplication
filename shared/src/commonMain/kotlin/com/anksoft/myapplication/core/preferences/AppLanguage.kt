package com.anksoft.myapplication.core.preferences

/**
 * The single list of languages the app can show. Adding a language is one entry here plus a
 * `values-xx/strings.xml` file (see docs/localization.md).
 *
 * [nativeName] is the language's own name and is deliberately not a string resource: a language
 * is always listed in itself ("Türkçe" stays "Türkçe" in the English UI), so the user can find
 * their language whatever the current one is. [SYSTEM] has no name here because its label,
 * "System default", is translated like any other text.
 */
enum class AppLanguage(val tag: String?, val nativeName: String?) {
    SYSTEM(tag = null, nativeName = null),
    TURKISH(tag = "tr", nativeName = "Türkçe"),
    ENGLISH(tag = "en", nativeName = "English");

    companion object {
        /** Unknown, blank or null tags fall back to [SYSTEM], so a removed language never crashes. */
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag != null && it.tag == tag } ?: SYSTEM
    }
}
