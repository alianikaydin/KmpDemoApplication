package com.anksoft.myapplication.core.storage

import java.util.concurrent.locks.ReentrantLock

internal actual fun createSessionLock(): SessionLock = object : SessionLock {
    private val lock = ReentrantLock()

    override fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
