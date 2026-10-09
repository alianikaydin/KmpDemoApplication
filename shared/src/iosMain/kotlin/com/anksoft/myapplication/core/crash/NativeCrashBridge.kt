package com.anksoft.myapplication.core.crash

/**
 * Implemented in Swift (`FirebaseCrashBridge.swift`), because the Firebase SDK is a Swift package
 * that Kotlin cannot import. Swift-friendly types only: no suspend functions, no Throwable.
 */
interface NativeCrashBridge {
    fun setCollectionEnabled(enabled: Boolean)

    fun deleteUnsentReports()

    fun deleteInstallationId()

    /** An empty [id] clears the user id. */
    fun setUserId(id: String)

    fun setCustomValue(key: String, value: String)

    fun log(message: String)

    /**
     * Records an error with the given Kotlin stack [stackAddresses] (top frame first). [reason] is
     * text we wrote and never contains a throwable message. [fatal] marks an error that is about
     * to end the process; the Firebase iOS SDK has no on-demand fatal API, so the Swift side
     * records it like any other error.
     */
    fun recordError(name: String, reason: String, stackAddresses: List<Long>, fatal: Boolean)
}
