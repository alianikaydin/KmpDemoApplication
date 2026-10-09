package com.anksoft.myapplication.features.consent.domain.model

/**
 * A decision the user made on screen. The version and language are those of the text that was
 * shown, so the backend can keep them as proof (AC-22).
 */
data class ConsentChoice(
    val granted: Boolean,
    val textVersion: Int,
    val textLanguage: String
) {
    companion object {
        fun of(granted: Boolean, texts: ConsentTexts): ConsentChoice =
            ConsentChoice(granted = granted, textVersion = texts.version, textLanguage = texts.language)
    }
}
