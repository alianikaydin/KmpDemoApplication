package com.anksoft.myapplication.core.storage

/**
 * Re-entrant mutual exclusion for session state.
 *
 * The session is written from several threads: Main (login, logout), the HTTP client's dispatcher
 * (token refresh) and the application scope (consent). Each compound change (check, then write)
 * must run as one step, and a thread that already holds the lock may take it again. Only
 * non-suspending code runs under it; never call a suspend function while holding it.
 */
interface SessionLock {
    fun <T> withLock(block: () -> T): T
}

/** Creates the platform's lock: a real one where threads exist, a pass-through on JS and Wasm. */
internal expect fun createSessionLock(): SessionLock
