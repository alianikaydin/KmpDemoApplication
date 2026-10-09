package com.anksoft.myapplication.core.concurrency

import java.util.concurrent.locks.ReentrantLock as JvmReentrantLock

internal actual fun createReentrantLock(): ReentrantLock = object : ReentrantLock {
    private val lock = JvmReentrantLock()

    override fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
