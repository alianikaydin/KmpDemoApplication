package com.anksoft.myapplication.core.consent

/** Decision on optional data collection (crash reporting, later analytics), as seen by consumers. */
enum class OptionalDataConsent {
    /** The account's confirmed decision is "granted". The only state that allows collection. */
    GRANTED,

    /** The account's confirmed decision is "denied": refused or withdrawn (on this or another device). */
    DENIED,

    /** No decision to act on: signed out, loading, fetch failed, or the account has not decided yet. */
    UNKNOWN,
}
