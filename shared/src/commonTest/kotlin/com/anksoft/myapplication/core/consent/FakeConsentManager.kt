package com.anksoft.myapplication.core.consent

import kotlinx.coroutines.flow.MutableStateFlow

/** A [ConsentManager] whose answer the test sets. Used by every feature that asks for consent. */
class FakeConsentManager(initial: OptionalDataConsent = OptionalDataConsent.UNKNOWN) : ConsentManager {
    override val optionalDataConsent = MutableStateFlow(initial)
}
