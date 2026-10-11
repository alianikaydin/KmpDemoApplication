package com.anksoft.myapplication.core.concurrency

/**
 * Re-entrant mutual exclusion for state that several threads change, such as the session (Main for
 * login and logout, the HTTP client's dispatcher for token refresh, the application scope for consent).
 * Each compound change (check, then write) must run as one step, and a thread that already holds the
 * lock may take it again. Only non-suspending code runs under it; never call a suspend function
 * while holding it.
 */
interface ReentrantLock {
    fun <T> withLock(block: () -> T): T
}

/** Creates the platform's lock: a real one where threads exist, a pass-through on JS and Wasm. */
internal expect fun createReentrantLock(): ReentrantLock
