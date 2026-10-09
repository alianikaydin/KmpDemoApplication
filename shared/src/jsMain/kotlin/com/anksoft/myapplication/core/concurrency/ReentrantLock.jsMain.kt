package com.anksoft.myapplication.core.concurrency

// The web runs this code on one thread, so there is nothing to exclude.
internal actual fun createReentrantLock(): ReentrantLock = object : ReentrantLock {
    override fun <T> withLock(block: () -> T): T = block()
}
