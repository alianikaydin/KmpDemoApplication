package com.anksoft.myapplication.features.profile.data.datasource

import com.anksoft.myapplication.core.database.AppDatabase
import com.anksoft.myapplication.core.database.AppDatabaseProvider
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.EmptyResult
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.warn
import com.anksoft.myapplication.features.profile.domain.model.AccountProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * [ProfileLocalDataSource] on the app database. Where there is no database (web) or it could not be
 * opened, it behaves like an empty store that accepts writes: nothing is found and nothing is kept
 * (AC-17, AC-23). A failing query is logged by action and error type only (AC-15).
 */
class SqlProfileLocalDataSource(
    private val provider: AppDatabaseProvider,
    private val dispatcher: CoroutineDispatcher,
    private val logger: AppLogger,
) : ProfileLocalDataSource {

    override suspend fun read(userId: String): Result<AccountProfile?, DataError.Local> =
        execute(action = "read", withoutDatabase = null) { database ->
            database.accountProfileQueries
                .selectByUserId(userId) { storedUserId, email, name -> AccountProfile(storedUserId, email, name) }
                .executeAsOneOrNull()
        }

    override suspend fun replaceWith(profile: AccountProfile): EmptyResult<DataError.Local> =
        execute(action = "write", withoutDatabase = Unit) { database ->
            database.transaction {
                database.accountProfileQueries.deleteAllExcept(profile.userId)
                database.accountProfileQueries.insertOrReplace(profile.userId, profile.email, profile.name)
            }
        }

    override suspend fun retainOnly(userId: String?): EmptyResult<DataError.Local> =
        execute(action = "delete", withoutDatabase = Unit) { database ->
            if (userId == null) {
                database.accountProfileQueries.deleteAll()
            } else {
                database.accountProfileQueries.deleteAllExcept(userId)
            }
        }

    private suspend fun <T> execute(
        action: String,
        withoutDatabase: T,
        block: (AppDatabase) -> T,
    ): Result<T, DataError.Local> = withContext(dispatcher) {
        val database = provider.databaseOrNull()
        if (database == null) {
            Result.Success(withoutDatabase)
        } else {
            try {
                Result.Success(block(database))
            } catch (e: Exception) {
                // Cancellation must stay cancellation, not become a storage error.
                currentCoroutineContext().ensureActive()
                val error = DataError.Local.UNKNOWN
                logger.warn(LogTags.PROFILE, e) { "profile $action failed error=$error" }
                Result.Failure(error)
            }
        }
    }
}
