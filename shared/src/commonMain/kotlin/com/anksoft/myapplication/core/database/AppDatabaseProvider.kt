package com.anksoft.myapplication.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.anksoft.myapplication.core.concurrency.createReentrantLock
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.error

/**
 * Owns the one connection to [AppDatabase]. Opens it on first use, off the main thread (callers run
 * on a background dispatcher). A file that cannot be opened or migrated is deleted and opened once
 * more; if that fails too, the database stays unavailable for this process and the app works from
 * the network (AC-17). [driverFactory] is null where there is no database (web, AC-23).
 *
 * Log lines carry the action only: never a file path, SQL or stored data (AC-15).
 */
class AppDatabaseProvider(
    private val driverFactory: DatabaseDriverFactory?,
    private val logger: AppLogger,
) {

    private sealed interface DatabaseState {
        data object NotOpened : DatabaseState
        class Open(val database: AppDatabase) : DatabaseState
        data object Unavailable : DatabaseState
    }

    private val lock = createReentrantLock()

    // Guarded by lock.
    private var state: DatabaseState = DatabaseState.NotOpened

    /**
     * Blocking. Null when there is no database on this platform or it could not be opened.
     * Never throws.
     */
    fun databaseOrNull(): AppDatabase? {
        val factory = driverFactory ?: return null
        return lock.withLock {
            when (val current = state) {
                is DatabaseState.Open -> current.database
                DatabaseState.Unavailable -> null
                DatabaseState.NotOpened -> openOrRecreate(factory)
            }
        }
    }

    private fun openOrRecreate(factory: DatabaseDriverFactory): AppDatabase? {
        val database = try {
            open(factory)
        } catch (e: Exception) {
            logger.error(LogTags.DATABASE, e) { "database open failed; deleting the file and retrying" }
            // The file only holds re-downloadable data, so a file that cannot be opened is replaced.
            factory.deleteDatabaseFile()
            try {
                open(factory)
            } catch (retry: Exception) {
                logger.error(LogTags.DATABASE, retry) {
                    "database open failed again; running without the local database"
                }
                null
            }
        }
        state = if (database != null) DatabaseState.Open(database) else DatabaseState.Unavailable
        return database
    }

    /**
     * Creates the driver and runs a trivial query so that creating or migrating the file happens
     * here and not on the first real query (the Android driver opens the file lazily).
     */
    private fun open(factory: DatabaseDriverFactory): AppDatabase {
        val driver = factory.create()
        try {
            driver.verifyOpen()
            return AppDatabase(driver)
        } catch (e: Exception) {
            runCatching { driver.close() }
            throw e
        }
    }

    private fun SqlDriver.verifyOpen() {
        executeQuery(
            identifier = null,
            sql = "SELECT 1",
            mapper = { QueryResult.Value(Unit) },
            parameters = 0,
        )
    }
}
