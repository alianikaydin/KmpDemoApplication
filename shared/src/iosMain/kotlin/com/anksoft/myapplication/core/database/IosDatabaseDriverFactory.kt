package com.anksoft.myapplication.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseFileContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * Opens [AppDatabase] under Library/Caches: excluded from backups and may be purged by the system;
 * the database is a re-downloadable cache (S-4).
 */
internal class IosDatabaseDriverFactory : DatabaseDriverFactory {

    override fun create(): SqlDriver = NativeSqliteDriver(
        schema = AppDatabase.Schema,
        name = DATABASE_NAME,
        onConfiguration = { it.copy(extendedConfig = it.extendedConfig.copy(basePath = cachesDirectory())) },
    )

    override fun deleteDatabaseFile() {
        runCatching { DatabaseFileContext.deleteDatabase(DATABASE_NAME, cachesDirectory()) }
    }

    private fun cachesDirectory(): String =
        NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
}
