package com.anksoft.myapplication.features.consent.domain.model

/** The consent text the backend serves for one language (AC-21). Nothing of it is hard-coded in the app. */
data class ConsentTexts(
    val version: Int,
    val language: String,
    val label: String,
    val description: String,
    val policyUrl: String
) {
    /** Only https links are opened; anything else the backend sends is shown without a link. */
    val hasSafePolicyUrl: Boolean
        get() = policyUrl.startsWith("https://")
}
