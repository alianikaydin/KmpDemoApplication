package com.anksoft.myapplication.features.profile

import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.EmptyResult
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.features.profile.data.datasource.ProfileLocalDataSource
import com.anksoft.myapplication.features.profile.domain.model.AccountProfile
import kotlinx.coroutines.CompletableDeferred

/** In-memory [ProfileLocalDataSource] that records its calls and can be told to fail or to wait. */
class FakeProfileLocalDataSource(initialRows: List<AccountProfile> = emptyList()) : ProfileLocalDataSource {

    /** The stored rows by user id. */
    val rows: MutableMap<String, AccountProfile> = initialRows.associateBy { it.userId }.toMutableMap()

    /** One entry per call, in order: `read(id)`, `replaceWith(id)`, `retainOnly(id|null)`. */
    val calls: MutableList<String> = mutableListOf()

    var failReads: Boolean = false
    var failWrites: Boolean = false

    /** When set, every write (replaceWith, retainOnly) suspends until it is completed. */
    var writeGate: CompletableDeferred<Unit>? = null

    override suspend fun read(userId: String): Result<AccountProfile?, DataError.Local> {
        calls += "read($userId)"
        return if (failReads) Result.Failure(DataError.Local.UNKNOWN) else Result.Success(rows[userId])
    }

    override suspend fun replaceWith(profile: AccountProfile): EmptyResult<DataError.Local> {
        calls += "replaceWith(${profile.userId})"
        writeGate?.await()
        if (failWrites) return Result.Failure(DataError.Local.UNKNOWN)
        rows.clear()
        rows[profile.userId] = profile
        return Result.Success(Unit)
    }

    override suspend fun retainOnly(userId: String?): EmptyResult<DataError.Local> {
        calls += "retainOnly($userId)"
        writeGate?.await()
        if (failWrites) return Result.Failure(DataError.Local.UNKNOWN)
        rows.keys.retainAll { it == userId }
        return Result.Success(Unit)
    }

    /** Calls to [replaceWith], i.e. writes of a profile. */
    val replaceCalls: List<String> get() = calls.filter { it.startsWith("replaceWith(") }
}
