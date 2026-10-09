package com.anksoft.myapplication.core.storage

// The web runs this code on one thread, so there is nothing to exclude.
internal actual fun createSessionLock(): SessionLock = object : SessionLock {
    override fun <T> withLock(block: () -> T): T = block()
}
