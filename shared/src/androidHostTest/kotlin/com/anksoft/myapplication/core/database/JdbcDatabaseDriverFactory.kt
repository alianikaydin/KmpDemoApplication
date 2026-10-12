package com.anksoft.myapplication.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/** Host-test factory that runs real SQLite through JDBC (no Robolectric). Counts the file deletions. */
class JdbcDatabaseDriverFactory(private val file: File) : DatabaseDriverFactory {

    var deleteCalls: Int = 0
        private set

    override fun create(): SqlDriver {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
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
        return driver
    }

    override fun deleteDatabaseFile() {
        deleteCalls++
        file.delete()
    }
}
