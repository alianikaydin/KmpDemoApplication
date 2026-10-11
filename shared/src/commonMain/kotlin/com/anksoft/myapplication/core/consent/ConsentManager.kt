package com.anksoft.myapplication.core.consent

import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of consent for every optional data-collection capability. A capability asks
 * here before it sends anything and treats everything except [OptionalDataConsent.GRANTED] as
 * "do not collect". Pure Kotlin: no Ktor, Compose, Koin or platform imports in this package.
 */
interface ConsentManager {
    /** Never throws; the initial value is available without suspending. */
    val optionalDataConsent: StateFlow<OptionalDataConsent>
}
