package com.anksoft.myapplication.core.storage

import platform.Foundation.NSRecursiveLock

internal actual fun createSessionLock(): SessionLock = object : SessionLock {
    private val lock = NSRecursiveLock()

    override fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
