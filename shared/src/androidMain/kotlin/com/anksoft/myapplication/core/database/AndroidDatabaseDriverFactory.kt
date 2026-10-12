package com.anksoft.myapplication.core.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/** Opens [AppDatabase] in the app's private `databases/` directory (excluded from backups, S-4). */
internal class AndroidDatabaseDriverFactory(private val context: Context) : DatabaseDriverFactory {

    override fun create(): SqlDriver = AndroidSqliteDriver(AppDatabase.Schema, context, DATABASE_NAME)

    /** The framework also removes the `-journal`, `-wal` and `-shm` files. */
    override fun deleteDatabaseFile() {
        runCatching { context.deleteDatabase(DATABASE_NAME) }
    }
}
