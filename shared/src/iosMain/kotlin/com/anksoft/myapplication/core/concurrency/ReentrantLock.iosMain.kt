package com.anksoft.myapplication.core.concurrency

import platform.Foundation.NSRecursiveLock

internal actual fun createReentrantLock(): ReentrantLock = object : ReentrantLock {
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
