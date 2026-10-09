package com.anksoft.myapplication.core.concurrency

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ReentrantLockTest {

    private val lock = createReentrantLock()

    // N2: read-modify-write steps from several threads must not interleave.
    @Test
    fun concurrentReadModifyWriteStepsUnderTheLockAreNotLost() = runTest {
        var counter = 0

        withContext(Dispatchers.Default) {
            (1..8).map {
                async {
                    repeat(5_000) {
                        lock.withLock {
                            val current = counter
                            counter = current + 1
                        }
                    }
                }
            }.awaitAll()
        }

        assertThat(counter).isEqualTo(40_000)
    }

    // N2: a callback that runs under the lock reads the session again.
    @Test
    fun theLockCanBeTakenAgainByTheThreadThatHoldsIt() {
        var innerRan = false

        lock.withLock { lock.withLock { innerRan = true } }

        assertThat(innerRan).isTrue()
    }

    @Test
    fun withLockReturnsTheValueOfTheBlock() {
        assertThat(lock.withLock { 42 }).isEqualTo(42)
    }

    @Test
    fun theLockIsReleasedWhenTheBlockThrows() = runTest {
        assertFailsWith<IllegalStateException> { lock.withLock { error("boom") } }

        // Another thread can still take the lock afterwards (a leaked lock would block here).
        val value = withContext(Dispatchers.Default) { lock.withLock { 7 } }

        assertThat(value).isEqualTo(7)
    }
}
