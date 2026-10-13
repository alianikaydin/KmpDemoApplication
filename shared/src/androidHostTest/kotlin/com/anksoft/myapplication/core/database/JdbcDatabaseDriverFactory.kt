package com.anksoft.myapplication.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/**
 * Host-test factory that runs real SQLite through JDBC (no Robolectric). Counts the calls so tests
 * can see how often the file was opened and deleted.
 */
class JdbcDatabaseDriverFactory(private val file: File) : DatabaseDriverFactory {

    private val drivers = mutableListOf<SqlDriver>()

    var createCalls: Int = 0
        private set

    var deleteCalls: Int = 0
        private set

    override fun create(): SqlDriver {
        createCalls++
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        try {
            val version = driver.executeQuery(
                identifier = null,
                sql = "PRAGMA user_version",
                mapper = { cursor ->
                    QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
                },
                parameters = 0,
            ).value ?: 0L
            // The JDBC driver does not create the schema itself; mirror what the platform drivers do.
            if (version == 0L) {
                AppDatabase.Schema.create(driver)
                driver.execute(null, "PRAGMA user_version = ${AppDatabase.Schema.version}", 0)
            }
        } catch (e: Exception) {
            driver.close()
            throw e
        }
        drivers += driver
        return driver
    }

    /** Closes every driver this factory created, like a process that ends before the next start. */
    fun closeDrivers() {
        drivers.forEach { it.close() }
        drivers.clear()
    }

    override fun deleteDatabaseFile() {
        deleteCalls++
        file.delete()
    }
}
