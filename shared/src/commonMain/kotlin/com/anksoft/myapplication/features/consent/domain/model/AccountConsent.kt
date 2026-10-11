package com.anksoft.myapplication.features.consent.domain.model

/** What the backend recorded for the account. */
enum class ConsentDecision { NONE, GRANTED, DENIED }

/**
 * The account's consent as the backend reports it. [reconsentRequired] is true when a text
 * version that needs a new consent was published after [textVersion] (AC-23).
 */
data class AccountConsent(
    val decision: ConsentDecision,
    val textVersion: Int?,
    val reconsentRequired: Boolean = false
)
