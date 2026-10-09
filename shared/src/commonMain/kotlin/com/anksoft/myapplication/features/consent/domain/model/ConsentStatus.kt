package com.anksoft.myapplication.features.consent.domain.model

import com.anksoft.myapplication.core.consent.OptionalDataConsent

/**
 * What the consent screens know about the account's decision. Consumers outside the consent
 * feature see only [OptionalDataConsent].
 */
sealed interface ConsentStatus {
    /** No session: signed out, logged out or the session expired. */
    data object SignedOut : ConsentStatus

    /** Signed in, nothing cached yet, first fetch running. */
    data object Loading : ConsentStatus

    /** Signed in, no known decision and the fetch failed (AC-12, AC-13). */
    data object Unavailable : ConsentStatus

    /** The backend has no decision for this account yet (or sent a status this app does not know). */
    data object Undecided : ConsentStatus

    /**
     * The backend has "granted" on record.
     *
     * @property reconsentRequired a newer text asks for a new consent; the old grant no longer counts.
     * @property suppressed the user switched collection off and the backend has not confirmed it
     * (or the write failed); collection stays off while the screens still show the stored grant.
     */
    data class Granted(
        val reconsentRequired: Boolean = false,
        val suppressed: Boolean = false
    ) : ConsentStatus

    /** The backend has "denied" on record. */
    data object Denied : ConsentStatus
}

/** The only mapping to what other features see. Everything but a confirmed, current grant is off. */
fun ConsentStatus.toOptionalDataConsent(): OptionalDataConsent = when (this) {
    ConsentStatus.SignedOut,
    ConsentStatus.Loading,
    ConsentStatus.Unavailable,
    ConsentStatus.Undecided -> OptionalDataConsent.UNKNOWN

    is ConsentStatus.Granted -> when {
        suppressed -> OptionalDataConsent.DENIED
        reconsentRequired -> OptionalDataConsent.UNKNOWN
        else -> OptionalDataConsent.GRANTED
    }

    ConsentStatus.Denied -> OptionalDataConsent.DENIED
}

/** True when the one-time consent prompt should be offered (AC-7, AC-23). */
val ConsentStatus.needsPrompt: Boolean
    get() = this == ConsentStatus.Undecided ||
        (this is ConsentStatus.Granted && reconsentRequired && !suppressed)

fun AccountConsent.toStatus(): ConsentStatus = when (decision) {
    ConsentDecision.NONE -> ConsentStatus.Undecided
    ConsentDecision.DENIED -> ConsentStatus.Denied
    ConsentDecision.GRANTED -> ConsentStatus.Granted(reconsentRequired = reconsentRequired)
}
