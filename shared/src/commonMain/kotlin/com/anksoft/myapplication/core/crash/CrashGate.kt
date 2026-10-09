package com.anksoft.myapplication.core.crash

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Whether caught errors and breadcrumbs may be handed to the vendor right now. Closed by default,
 * so nothing is sent until the controller has seen a granted consent.
 */
class CrashGate {
    private val state = MutableStateFlow(false)

    val isOpen: Boolean get() = state.value

    internal fun open() {
        state.value = true
    }

    internal fun close() {
        state.value = false
    }
}
