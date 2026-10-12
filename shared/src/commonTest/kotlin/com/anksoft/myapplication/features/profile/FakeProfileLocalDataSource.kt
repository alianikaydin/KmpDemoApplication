package com.anksoft.myapplication.features.profile

import com.anksoft.myapplication.core.concurrency.createReentrantLock
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.EmptyResult
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.features.profile.data.datasource.ProfileLocalDataSource
import com.anksoft.myapplication.features.profile.domain.model.AccountProfile
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [ProfileLocalDataSource] that records its calls and can be told to fail or to wait.
 * Wiring tests use it from the application scope's threads, so its state is guarded by a lock.
 */
class FakeProfileLocalDataSource(initialRows: List<AccountProfile> = emptyList()) : ProfileLocalDataSource {

    private val lock = createReentrantLock()
    private val storedRows = initialRows.associateBy { it.userId }.toMutableMap()
    private val recordedCalls = mutableListOf<String>()

    /** The stored rows by user id, as they are now. */
    val rows: Map<String, AccountProfile> get() = lock.withLock { storedRows.toMap() }

    /** One entry per call, in order: `read(id)`, `replaceWith(id)`, `retainOnly(id|null)`. */
    val calls: List<String> get() = lock.withLock { recordedCalls.toList() }

    /** Calls to [replaceWith], i.e. writes of a profile. */
    val replaceCalls: List<String> get() = calls.filter { it.startsWith("replaceWith(") }

    var failReads: Boolean = false
    var failWrites: Boolean = false

    /** When set, every write (replaceWith, retainOnly) suspends until it is completed. */
    var writeGate: CompletableDeferred<Unit>? = null

    /** Puts a row in the store without recording a call, like a file left by an earlier run. */
    fun seed(profile: AccountProfile) {
        lock.withLock { storedRows[profile.userId] = profile }
    }

    override suspend fun read(userId: String): Result<AccountProfile?, DataError.Local> {
        lock.withLock { recordedCalls += "read($userId)" }
        return if (failReads) Result.Failure(DataError.Local.UNKNOWN) else Result.Success(rows[userId])
    }

    override suspend fun replaceWith(profile: AccountProfile): EmptyResult<DataError.Local> {
        lock.withLock { recordedCalls += "replaceWith(${profile.userId})" }
        writeGate?.await()
        if (failWrites) return Result.Failure(DataError.Local.UNKNOWN)
        lock.withLock {
            storedRows.clear()
            storedRows[profile.userId] = profile
        }
        return Result.Success(Unit)
    }

    override suspend fun retainOnly(userId: String?): EmptyResult<DataError.Local> {
        lock.withLock { recordedCalls += "retainOnly($userId)" }
        writeGate?.await()
        if (failWrites) return Result.Failure(DataError.Local.UNKNOWN)
        lock.withLock { storedRows.keys.retainAll { it == userId } }
        return Result.Success(Unit)
    }
}
